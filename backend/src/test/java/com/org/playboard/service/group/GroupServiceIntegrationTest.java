package com.org.playboard.service.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.org.playboard.common.ApiException;
import com.org.playboard.dto.group.CreateGroupRequest;
import com.org.playboard.dto.group.CreateInviteRequest;
import com.org.playboard.dto.group.GroupSummaryDto;
import com.org.playboard.dto.group.InviteResponse;
import com.org.playboard.dto.group.JoinGroupRequest;
import com.org.playboard.dto.group.MembersResponse;
import com.org.playboard.entity.user.User;
import com.org.playboard.repository.user.UserRepository;
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

    private static User newUser() {
        User user = new User();
        user.setEmail("group-test-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Group Test User");
        user.setAvatarColor("#7ED321");
        return user;
    }
}
