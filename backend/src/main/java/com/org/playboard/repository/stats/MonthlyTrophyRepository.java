package com.org.playboard.repository.stats;

import com.org.playboard.entity.stats.MonthlyTrophy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface MonthlyTrophyRepository extends JpaRepository<MonthlyTrophy, UUID> {

    /**
     * Records a month's verdict unless one is already recorded, returning 1 when it claimed
     * the month and 0 when a concurrent or earlier scan already had it.
     *
     * <p>{@code on conflict do nothing} rather than catching the unique violation, for the
     * same reason as {@code NotificationLogRepository.insertIfAbsent}: a constraint breach
     * marks the surrounding transaction rollback-only, so a caller that caught it and carried
     * on would fail its own commit. Returning 0 here is also the signal not to push — a
     * second instance that lost the race must stay silent.
     *
     * <p>A null {@code userId} is a legitimate verdict, meaning the month was evaluated and
     * nobody qualified.
     *
     * <p>{@code @Transactional} lives here rather than on {@code MonthlyTrophyJob}: the job
     * calls this three levels deep through plain {@code this.} calls
     * ({@code awardCompletedMonths -> processGroup -> awardMonth}), and Spring's proxy-based
     * AOP does not intercept self-invocation, so an annotation up there would silently no-op.
     * This repository method is always invoked through its own Spring-managed proxy no matter
     * who calls it, so the transaction always actually opens.
     */
    @Transactional
    @Modifying
    @Query(
            value =
                    """
                    insert into monthly_trophy
                        (id, group_id, user_id, month, rating, games_played, wins, created_at, updated_at)
                    values
                        (gen_random_uuid(), :groupId, :userId, :month, :rating, :gamesPlayed, :wins, now(), now())
                    on conflict (group_id, month) do nothing
                    """,
            nativeQuery = true)
    int awardIfAbsent(
            @Param("groupId") UUID groupId,
            @Param("userId") UUID userId,
            @Param("month") LocalDate month,
            @Param("rating") BigDecimal rating,
            @Param("gamesPlayed") Integer gamesPlayed,
            @Param("wins") Integer wins);

    /** Months already decided for a group, winner or not — the job skips these. */
    @Query("select t.month from MonthlyTrophy t where t.groupId = :groupId")
    List<LocalDate> findDecidedMonths(@Param("groupId") UUID groupId);

    /**
     * A group's roll of honour, newest first. Excludes months with no winner: an empty month
     * is bookkeeping for the job, not something to render.
     */
    List<MonthlyTrophy> findByGroupIdAndUserIdIsNotNullOrderByMonthDesc(UUID groupId);

    /** One player's trophies in a group, newest first. */
    List<MonthlyTrophy> findByGroupIdAndUserIdOrderByMonthDesc(UUID groupId, UUID userId);
}
