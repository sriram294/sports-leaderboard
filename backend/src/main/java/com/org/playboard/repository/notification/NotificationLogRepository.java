package com.org.playboard.repository.notification;

import com.org.playboard.entity.notification.NotificationLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * Records a send unless it is already recorded, returning 1 when it claimed the slot
     * and 0 when someone else already had it.
     *
     * <p>{@code on conflict do nothing} rather than catching the unique violation: a
     * constraint breach marks the surrounding transaction rollback-only, so a caller that
     * caught it and returned normally would then fail its own commit. Letting Postgres
     * resolve the conflict keeps the transaction clean and the check atomic.
     */
    @Modifying
    @Query(
            value =
                    """
                    insert into notification_log (id, user_id, category, dedupe_key, created_at, updated_at)
                    values (gen_random_uuid(), :userId, :category, :dedupeKey, now(), now())
                    on conflict (user_id, category, dedupe_key) do nothing
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") UUID userId, @Param("category") String category, @Param("dedupeKey") String dedupeKey);
}
