package com.org.playboard.repository.user;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Transactional bulk operations used only by the account-deletion lifecycle. */
@Repository
public class AccountDeletionRepository {

    private static final String DELETED_NAME = "Deleted player";
    private static final String DELETED_AVATAR_COLOR = "#9AA0A6";

    private final JdbcTemplate jdbc;

    public AccountDeletionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Locks the account row and returns whether it is still active. */
    public boolean lockActiveUser(UUID userId) {
        return !jdbc.query(
                        "select id from users where id = ? and deleted_at is null for update",
                        (rs, rowNum) -> rs.getObject("id", UUID.class),
                        userId)
                .isEmpty();
    }

    /** Locks every group in which this account is the active owner. */
    public List<UUID> lockOwnedGroupIds(UUID userId) {
        return jdbc.query(
                """
                select gm.group_id
                from group_members gm
                join groups g on g.id = gm.group_id
                where gm.user_id = ? and gm.status = 'active' and gm.role = 'owner'
                order by gm.group_id
                for update of gm, g
                """,
                (rs, rowNum) -> rs.getObject("group_id", UUID.class),
                userId);
    }

    /** Oldest admin wins; if none exists, the oldest regular member wins. */
    public Optional<OwnershipCandidate> findOwnershipCandidate(UUID groupId, UUID deletingUserId) {
        return jdbc.query(
                        """
                        select id, user_id
                        from group_members
                        where group_id = ? and user_id <> ? and status = 'active'
                          and role in ('admin', 'member')
                        order by case role when 'admin' then 0 else 1 end, joined_at, id
                        limit 1
                        for update
                        """,
                        AccountDeletionRepository::candidate,
                        groupId,
                        deletingUserId)
                .stream()
                .findFirst();
    }

    public void promoteToOwner(UUID membershipId) {
        jdbc.update("update group_members set role = 'owner' where id = ?", membershipId);
    }

    public void archiveGroup(UUID groupId) {
        jdbc.update("update groups set is_active = false, updated_at = now() where id = ?", groupId);
        jdbc.update("delete from group_invites where group_id = ?", groupId);
    }

    public void removeMemberships(UUID userId) {
        jdbc.update(
                "update group_members set status = 'removed', role = 'member' where user_id = ? and status = 'active'",
                userId);
    }

    public void deleteAccountScopedRows(UUID userId) {
        jdbc.update("delete from group_invites where created_by = ?", userId);
        jdbc.update("delete from match_record_requests where requested_by = ?", userId);
        jdbc.update("delete from notification_log where user_id = ?", userId);
        jdbc.update("delete from device_tokens where user_id = ?", userId);
        jdbc.update("delete from refresh_tokens where user_id = ?", userId);
        jdbc.update("delete from user_auth_identities where user_id = ?", userId);
    }

    public void anonymizeUser(UUID userId, Instant deletedAt) {
        String deletedEmail = "deleted-" + userId + "@deleted.playboard.invalid";
        jdbc.update(
                """
                update users
                set google_sub = null,
                    email = ?,
                    display_name = ?,
                    photo_url = null,
                    avatar_id = null,
                    avatar_color = ?,
                    deleted_at = ?,
                    updated_at = ?
                where id = ?
                """,
                deletedEmail,
                DELETED_NAME,
                DELETED_AVATAR_COLOR,
                deletedAt,
                deletedAt,
                userId);
    }

    private static OwnershipCandidate candidate(ResultSet rs, int rowNum) throws SQLException {
        return new OwnershipCandidate(
                rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class));
    }

    public record OwnershipCandidate(UUID membershipId, UUID userId) {}
}
