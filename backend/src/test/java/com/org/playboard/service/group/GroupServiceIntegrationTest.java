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

    private static User newUser() {
        User user = new User();
        user.setEmail("group-test-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Group Test User");
        user.setAvatarColor("#7ED321");
        return user;
    }
}
