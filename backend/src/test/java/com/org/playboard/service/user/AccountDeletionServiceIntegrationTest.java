package com.org.playboard.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.org.playboard.entity.user.User;
import com.org.playboard.repository.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AccountDeletionServiceIntegrationTest {

    @Autowired private AccountDeletionService service;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void anonymizesSharedHistoryTransfersOwnershipAndArchivesAnEmptyGroup() {
        User owner = users.saveAndFlush(user("owner"));
        String originalEmail = owner.getEmail();
        User admin = users.saveAndFlush(user("admin"));
        User olderMember = users.saveAndFlush(user("member"));
        Short sportId = jdbc.queryForObject(
                "select id from sports where code = 'badminton_doubles'", Short.class);
        UUID sharedGroup = UUID.randomUUID();
        UUID emptyGroup = UUID.randomUUID();
        insertGroup(sharedGroup, sportId, owner.getId(), "Shared group");
        insertGroup(emptyGroup, sportId, owner.getId(), "Empty group");

        insertMembership(sharedGroup, owner.getId(), "owner", Instant.parse("2026-01-01T00:00:00Z"));
        insertMembership(sharedGroup, olderMember.getId(), "member", Instant.parse("2026-01-02T00:00:00Z"));
        insertMembership(sharedGroup, admin.getId(), "admin", Instant.parse("2026-01-03T00:00:00Z"));
        insertMembership(emptyGroup, owner.getId(), "owner", Instant.parse("2026-01-01T00:00:00Z"));

        jdbc.update(
                "insert into user_auth_identities (id, user_id, provider, subject) values (?, ?, 'google', ?)",
                UUID.randomUUID(), owner.getId(), "google-owner");
        UUID matchId = UUID.randomUUID();
        jdbc.update(
                "insert into matches (id, group_id, played_at, recorded_by) values (?, ?, now(), ?)",
                matchId, sharedGroup, owner.getId());
        UUID teamId = UUID.randomUUID();
        jdbc.update(
                "insert into match_teams (id, match_id, team_no, is_winner) values (?, ?, 1, true)",
                teamId, matchId);
        jdbc.update(
                "insert into match_participants (id, match_id, match_team_id, user_id) values (?, ?, ?, ?)",
                UUID.randomUUID(), matchId, teamId, owner.getId());

        service.deleteAccount(owner.getId(), "DELETE");

        var tombstone = jdbc.queryForMap(
                "select email, display_name, google_sub, photo_url, avatar_id, deleted_at from users where id = ?",
                owner.getId());
        assertThat(tombstone.get("email")).isEqualTo("deleted-" + owner.getId() + "@deleted.playboard.invalid");
        assertThat(tombstone.get("display_name")).isEqualTo("Deleted player");
        assertThat(tombstone.get("google_sub")).isNull();
        assertThat(tombstone.get("photo_url")).isNull();
        assertThat(tombstone.get("avatar_id")).isNull();
        assertThat(tombstone.get("deleted_at")).isNotNull();

        assertThat(role(sharedGroup, admin.getId())).isEqualTo("owner");
        assertThat(role(sharedGroup, olderMember.getId())).isEqualTo("member");
        assertThat(status(sharedGroup, owner.getId())).isEqualTo("removed");
        assertThat(jdbc.queryForObject("select is_active from groups where id = ?", Boolean.class, emptyGroup))
                .isFalse();
        assertThat(jdbc.queryForObject(
                        "select count(*) from user_auth_identities where user_id = ?", Long.class, owner.getId()))
                .isZero();
        assertThat(jdbc.queryForObject(
                        "select count(*) from match_participants where match_id = ? and user_id = ?",
                        Long.class, matchId, owner.getId()))
                .isOne();

        User freshAccount = users.saveAndFlush(userWithEmail("fresh", originalEmail));
        assertThat(freshAccount.getId()).isNotEqualTo(owner.getId());
        assertThat(jdbc.queryForObject(
                        "select count(*) from group_members where user_id = ?", Long.class, freshAccount.getId()))
                .isZero();
    }

    private User user(String prefix) {
        return userWithEmail(prefix, prefix + "-" + UUID.randomUUID() + "@example.com");
    }

    private User userWithEmail(String name, String email) {
        User user = new User();
        user.setEmail(email);
        user.setDisplayName(name);
        user.setAvatarColor("#7ED321");
        return user;
    }

    private void insertGroup(UUID id, Short sportId, UUID ownerId, String name) {
        jdbc.update(
                "insert into groups (id, sport_id, name, created_by, avatar_color) values (?, ?, ?, ?, '#7ED321')",
                id, sportId, name, ownerId);
    }

    private void insertMembership(UUID groupId, UUID userId, String role, Instant joinedAt) {
        jdbc.update(
                "insert into group_members (id, group_id, user_id, role, joined_at) values (?, ?, ?, ?, ?)",
                UUID.randomUUID(), groupId, userId, role, joinedAt);
    }

    private String role(UUID groupId, UUID userId) {
        return jdbc.queryForObject(
                "select role from group_members where group_id = ? and user_id = ?", String.class, groupId, userId);
    }

    private String status(UUID groupId, UUID userId) {
        return jdbc.queryForObject(
                "select status from group_members where group_id = ? and user_id = ?", String.class, groupId, userId);
    }
}
