package com.org.playboard.service.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.group.AddMemberRequest;
import com.org.playboard.dto.group.CreateGroupRequest;
import com.org.playboard.dto.group.CreateInviteRequest;
import com.org.playboard.dto.group.GroupSummaryDto;
import com.org.playboard.dto.group.InviteResponse;
import com.org.playboard.dto.group.JoinGroupRequest;
import com.org.playboard.dto.group.MemberDto;
import com.org.playboard.dto.group.MembersResponse;
import com.org.playboard.dto.group.RenameGroupRequest;
import com.org.playboard.dto.group.UpdateSessionRequest;
import com.org.playboard.entity.group.GroupMember;
import com.org.playboard.entity.group.MemberStatus;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.group.GroupMemberRepository;
import com.org.playboard.repository.user.UserRepository;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// Live-DB test covering the group lifecycle end to end: create (caller
// becomes owner) -> invite -> a second user joins -> roster reflects both ->
// permission checks reject a plain member creating invites and a stranger
// viewing the roster.
@SpringBootTest
@Transactional
class GroupServiceIntegrationTest {

    @Autowired private GroupService groupService;
    @Autowired private UserRepository userRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;

    @Test
    void createJoinInviteAndRosterFlow() {
        User owner = userRepository.save(newUser());
        User joiner = userRepository.save(newUser());
        User stranger = userRepository.save(newUser());

        GroupSummaryDto created = groupService.createGroup(
                owner.getId(), new CreateGroupRequest("Saturday Smashers", "badminton_doubles"));
        assertThat(created.myRole()).isEqualTo("owner");
        assertThat(created.memberCount()).isEqualTo(1);
        assertThat(created.matchCount()).isZero();
        assertThat(created.avatarColor()).isNotBlank();

        InviteResponse invite = groupService.createInvite(
                created.id(), owner.getId(), new CreateInviteRequest(5, 168));
        assertThat(invite.code()).hasSize(6);

        GroupSummaryDto joined = groupService.joinGroup(joiner.getId(), new JoinGroupRequest(invite.code()));
        assertThat(joined.id()).isEqualTo(created.id());
        assertThat(joined.myRole()).isEqualTo("member");
        assertThat(joined.memberCount()).isEqualTo(2);

        // Rejoining with the same still-valid code is idempotent, not an error.
        GroupSummaryDto rejoined = groupService.joinGroup(joiner.getId(), new JoinGroupRequest(invite.code()));
        assertThat(rejoined.memberCount()).isEqualTo(2);

        MembersResponse roster = groupService.listMembers(created.id(), owner.getId());
        // Real players only in members[] — guests are fillers, not counted as players.
        assertThat(roster.members()).extracting("userId").containsExactlyInAnyOrder(owner.getId(), joiner.getId());
        // Every group is seeded with its guest fillers, returned separately and
        // excluded from memberCount above.
        assertThat(roster.guests()).extracting("displayName").containsExactly("Guest 1", "Guest 2", "Guest 3");
        assertThat(roster.guests()).extracting("role").containsOnly("guest");

        // A plain member can't mint invites (owner/admin only).
        assertThatThrownBy(() -> groupService.createInvite(created.id(), joiner.getId(), new CreateInviteRequest(null, null)))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("GROUP_ROLE_FORBIDDEN"));

        // A non-member can't view the roster.
        assertThatThrownBy(() -> groupService.listMembers(created.id(), stranger.getId()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("GROUP_ACCESS_FORBIDDEN"));
    }

    @Test
    void guestFillersAreSharedAcrossGroups() {
        User ownerA = userRepository.save(newUser());
        User ownerB = userRepository.save(newUser());

        GroupSummaryDto groupA =
                groupService.createGroup(ownerA.getId(), new CreateGroupRequest("Group A", "badminton_doubles"));
        GroupSummaryDto groupB =
                groupService.createGroup(ownerB.getId(), new CreateGroupRequest("Group B", "badminton_doubles"));

        MembersResponse rosterA = groupService.listMembers(groupA.id(), ownerA.getId());
        MembersResponse rosterB = groupService.listMembers(groupB.id(), ownerB.getId());

        // Each group still exposes exactly its 3 guest fillers, named/colored the same.
        assertThat(rosterA.guests()).extracting("displayName").containsExactly("Guest 1", "Guest 2", "Guest 3");
        assertThat(rosterA.guests()).extracting("role").containsOnly("guest");
        assertThat(rosterA.guests()).extracting("avatarColor").containsOnly("#9AA0A6");

        // ...but both groups reference the SAME 3 shared guest user rows — no new
        // synthetic users are minted per group.
        assertThat(rosterB.guests())
                .extracting("userId")
                .containsExactlyElementsOf(rosterA.guests().stream().map(MemberDto::userId).toList());

        // Sharing guests doesn't leak them into member counts.
        assertThat(groupA.memberCount()).isEqualTo(1);
        assertThat(groupB.memberCount()).isEqualTo(1);
    }

    @Test
    void renameGroupUpdatesNameForOwnerButRejectsPlainMember() {
        User owner = userRepository.save(newUser());
        User joiner = userRepository.save(newUser());

        GroupSummaryDto created = groupService.createGroup(
                owner.getId(), new CreateGroupRequest("Original Name", "badminton_doubles"));
        String colorBefore = created.avatarColor();

        InviteResponse invite = groupService.createInvite(created.id(), owner.getId(), new CreateInviteRequest(5, null));
        groupService.joinGroup(joiner.getId(), new JoinGroupRequest(invite.code()));

        // Owner renames — name changes, avatar color stays stable.
        GroupSummaryDto renamed =
                groupService.renameGroup(created.id(), owner.getId(), new RenameGroupRequest("  Renamed Group  "));
        assertThat(renamed.name()).isEqualTo("Renamed Group");
        assertThat(renamed.avatarColor()).isEqualTo(colorBefore);

        // The change persists and is visible to other members.
        assertThat(groupService.listGroupsForUser(joiner.getId()).groups())
                .extracting("name")
                .containsExactly("Renamed Group");

        // A plain member can't rename (owner/admin only).
        assertThatThrownBy(
                        () -> groupService.renameGroup(created.id(), joiner.getId(), new RenameGroupRequest("Hijacked")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("GROUP_ROLE_FORBIDDEN"));
    }

    @Test
    void invalidInviteCodeIsRejected() {
        User user = userRepository.save(newUser());

        assertThatThrownBy(() -> groupService.joinGroup(user.getId(), new JoinGroupRequest("NOPE99")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("GROUP_INVITE_INVALID"));
    }

    @Test
    void exhaustedInviteIsRejected() {
        User owner = userRepository.save(newUser());
        User first = userRepository.save(newUser());
        User second = userRepository.save(newUser());

        GroupSummaryDto created =
                groupService.createGroup(owner.getId(), new CreateGroupRequest("Single Use Group", "badminton_doubles"));
        InviteResponse invite = groupService.createInvite(created.id(), owner.getId(), new CreateInviteRequest(1, null));

        groupService.joinGroup(first.getId(), new JoinGroupRequest(invite.code()));

        assertThatThrownBy(() -> groupService.joinGroup(second.getId(), new JoinGroupRequest(invite.code())))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("GROUP_INVITE_INVALID"));
    }

    @Test
    void addMemberByEmailCreatesRealMemberAndNormalizesEmail() {
        User owner = userRepository.save(newUser());
        GroupSummaryDto group =
                groupService.createGroup(owner.getId(), new CreateGroupRequest("Add By Email", "badminton_doubles"));

        // Mixed-case, padded email + padded name -> real MEMBER, normalized + trimmed.
        MemberDto added = groupService.addMemberByEmail(
                group.id(), owner.getId(), new AddMemberRequest("  New.Person+X@GMAIL.com ", "  New Person  "));
        assertThat(added.role()).isEqualTo("member");
        assertThat(added.displayName()).isEqualTo("New Person");

        User createdUser = userRepository.findById(added.userId()).orElseThrow();
        assertThat(createdUser.getEmail()).isEqualTo("new.person+x@gmail.com"); // trimmed + lowercased
        assertThat(createdUser.getGoogleSub()).isNull(); // provisional — claimed on first sign-in
        assertThat(createdUser.getAvatarColor()).isNotBlank();

        // Shows in the roster as a real member (not a guest) and counts toward memberCount.
        MembersResponse roster = groupService.listMembers(group.id(), owner.getId());
        assertThat(roster.members()).extracting("userId").contains(added.userId());
        assertThat(roster.guests()).extracting("userId").doesNotContain(added.userId());
        assertThat(groupService.listGroupsForUser(owner.getId()).groups().get(0).memberCount()).isEqualTo(2);

        // Re-adding the same email (any casing) is a 409, never a duplicate.
        assertThatThrownBy(() -> groupService.addMemberByEmail(
                        group.id(), owner.getId(), new AddMemberRequest("NEW.PERSON+x@gmail.com", "Dup")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("GROUP_MEMBER_EXISTS"));
    }

    @Test
    void addMemberByEmailReusesExistingUserRow() {
        User owner = userRepository.save(newUser());
        User existing = userRepository.save(newUser()); // already a Playboard user
        GroupSummaryDto group =
                groupService.createGroup(owner.getId(), new CreateGroupRequest("Reuse", "badminton_doubles"));

        MemberDto added = groupService.addMemberByEmail(
                group.id(), owner.getId(),
                new AddMemberRequest(existing.getEmail().toUpperCase(Locale.ROOT), "Ignored Name"));

        assertThat(added.userId()).isEqualTo(existing.getId()); // reused, not duplicated
        assertThat(added.displayName()).isEqualTo(existing.getDisplayName()); // their identity wins
    }

    @Test
    void addMemberByEmailReactivatesRemovedMember() {
        User owner = userRepository.save(newUser());
        GroupSummaryDto group =
                groupService.createGroup(owner.getId(), new CreateGroupRequest("Reactivate", "badminton_doubles"));
        MemberDto added = groupService.addMemberByEmail(
                group.id(), owner.getId(), new AddMemberRequest("gone@example.com", "Gone"));

        GroupMember membership =
                groupMemberRepository.findByGroupIdAndUserId(group.id(), added.userId()).orElseThrow();
        membership.setStatus(MemberStatus.REMOVED);
        groupMemberRepository.save(membership);

        // Re-adding reactivates the same row — no 409, no duplicate.
        MemberDto readded = groupService.addMemberByEmail(
                group.id(), owner.getId(), new AddMemberRequest("gone@example.com", "Gone"));
        assertThat(readded.userId()).isEqualTo(added.userId());
        assertThat(groupService.listMembers(group.id(), owner.getId()).members())
                .extracting("userId")
                .contains(added.userId());
    }

    @Test
    void addMemberByEmailRejectsPlainMember() {
        User owner = userRepository.save(newUser());
        User plain = userRepository.save(newUser());
        GroupSummaryDto group =
                groupService.createGroup(owner.getId(), new CreateGroupRequest("Gated", "badminton_doubles"));
        InviteResponse invite =
                groupService.createInvite(group.id(), owner.getId(), new CreateInviteRequest(null, null));
        groupService.joinGroup(plain.getId(), new JoinGroupRequest(invite.code()));

        // Owner/admin only — a plain member can't add others.
        assertThatThrownBy(() -> groupService.addMemberByEmail(
                        group.id(), plain.getId(), new AddMemberRequest("someone@example.com", "Someone")))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo("GROUP_ROLE_FORBIDDEN"));
    }

    @Test
    void removeMemberSoftRemovesAndEnforcesRules() {
        User owner = userRepository.save(newUser());
        GroupSummaryDto group =
                groupService.createGroup(owner.getId(), new CreateGroupRequest("Manage", "badminton_doubles"));
        MemberDto m1 = groupService.addMemberByEmail(group.id(), owner.getId(), new AddMemberRequest("m1@example.com", "M One"));
        MemberDto m2 = groupService.addMemberByEmail(group.id(), owner.getId(), new AddMemberRequest("m2@example.com", "M Two"));

        // Owner can't remove self or a guest filler.
        assertApiCode(() -> groupService.removeMember(group.id(), owner.getId(), owner.getId()), "GROUP_CANNOT_REMOVE_SELF");
        UUID guestId = groupService.listMembers(group.id(), owner.getId()).guests().get(0).userId();
        assertApiCode(() -> groupService.removeMember(group.id(), owner.getId(), guestId), "GROUP_CANNOT_REMOVE_GUEST");

        // Owner removes m1 -> drops from the roster (soft remove).
        groupService.removeMember(group.id(), owner.getId(), m1.userId());
        assertThat(groupService.listMembers(group.id(), owner.getId()).members())
                .extracting("userId").doesNotContain(m1.userId()).contains(m2.userId());
        assertThat(groupMemberRepository.findByGroupIdAndUserId(group.id(), m1.userId()).orElseThrow().getStatus())
                .isEqualTo(MemberStatus.REMOVED);

        // Promote m2 -> admin. An admin can't remove the owner or another admin, but can remove a member.
        groupService.changeMemberRole(group.id(), owner.getId(), m2.userId(), "admin");
        MemberDto m3 = groupService.addMemberByEmail(group.id(), owner.getId(), new AddMemberRequest("m3@example.com", "M Three"));
        groupService.changeMemberRole(group.id(), owner.getId(), m3.userId(), "admin");
        assertApiCode(() -> groupService.removeMember(group.id(), m2.userId(), owner.getId()), "GROUP_OWNER_PROTECTED");
        assertApiCode(() -> groupService.removeMember(group.id(), m2.userId(), m3.userId()), "GROUP_ROLE_FORBIDDEN");

        MemberDto m4 = groupService.addMemberByEmail(group.id(), owner.getId(), new AddMemberRequest("m4@example.com", "M Four"));
        groupService.removeMember(group.id(), m2.userId(), m4.userId());
        assertThat(groupService.listMembers(group.id(), owner.getId()).members())
                .extracting("userId").doesNotContain(m4.userId());
    }

    @Test
    void changeMemberRoleIsOwnerOnlyAndProtectsOwner() {
        User owner = userRepository.save(newUser());
        GroupSummaryDto group =
                groupService.createGroup(owner.getId(), new CreateGroupRequest("Roles", "badminton_doubles"));
        MemberDto m1 = groupService.addMemberByEmail(group.id(), owner.getId(), new AddMemberRequest("r1@example.com", "R One"));

        // Owner promotes then demotes.
        assertThat(groupService.changeMemberRole(group.id(), owner.getId(), m1.userId(), "admin").role()).isEqualTo("admin");
        assertThat(groupService.changeMemberRole(group.id(), owner.getId(), m1.userId(), "member").role()).isEqualTo("member");

        // Owner can't assign OWNER, can't change own role.
        assertApiCode(() -> groupService.changeMemberRole(group.id(), owner.getId(), m1.userId(), "owner"), "GROUP_ROLE_INVALID");
        assertApiCode(() -> groupService.changeMemberRole(group.id(), owner.getId(), owner.getId(), "member"), "GROUP_CANNOT_CHANGE_OWN_ROLE");

        // An admin (not owner) can't change roles.
        groupService.changeMemberRole(group.id(), owner.getId(), m1.userId(), "admin");
        MemberDto m2 = groupService.addMemberByEmail(group.id(), owner.getId(), new AddMemberRequest("r2@example.com", "R Two"));
        assertApiCode(() -> groupService.changeMemberRole(group.id(), m1.userId(), m2.userId(), "admin"), "GROUP_ROLE_FORBIDDEN");
    }

    @Test
    void updateSessionSetsClearsAndValidates() {
        User owner = userRepository.save(newUser());
        User plain = userRepository.save(newUser());
        GroupSummaryDto group =
                groupService.createGroup(owner.getId(), new CreateGroupRequest("Session", "badminton_doubles"));
        InviteResponse invite = groupService.createInvite(group.id(), owner.getId(), new CreateInviteRequest(null, null));
        groupService.joinGroup(plain.getId(), new JoinGroupRequest(invite.code()));

        GroupSummaryDto set = groupService.updateSession(group.id(), owner.getId(), new UpdateSessionRequest("19:00", "21:00"));
        assertThat(set.sessionStart()).isEqualTo("19:00");
        assertThat(set.sessionEnd()).isEqualTo("21:00");
        // Persists onto the group list.
        assertThat(groupService.listGroupsForUser(owner.getId()).groups().get(0).sessionStart()).isEqualTo("19:00");

        GroupSummaryDto cleared = groupService.updateSession(group.id(), owner.getId(), new UpdateSessionRequest(null, null));
        assertThat(cleared.sessionStart()).isNull();
        assertThat(cleared.sessionEnd()).isNull();

        // Invalid: start >= end, only one provided, bad format.
        assertApiCode(() -> groupService.updateSession(group.id(), owner.getId(), new UpdateSessionRequest("21:00", "19:00")), "GROUP_SESSION_INVALID");
        assertApiCode(() -> groupService.updateSession(group.id(), owner.getId(), new UpdateSessionRequest("19:00", null)), "GROUP_SESSION_INVALID");
        assertApiCode(() -> groupService.updateSession(group.id(), owner.getId(), new UpdateSessionRequest("7pm", "9pm")), "GROUP_SESSION_INVALID");

        // Owner/admin only.
        assertApiCode(() -> groupService.updateSession(group.id(), plain.getId(), new UpdateSessionRequest("19:00", "21:00")), "GROUP_ROLE_FORBIDDEN");
    }

    private static void assertApiCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).getCode()).isEqualTo(code));
    }

    private static User newUser() {
        User user = new User();
        user.setEmail("group-test-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Group Test User");
        user.setAvatarColor("#7ED321");
        return user;
    }
}
