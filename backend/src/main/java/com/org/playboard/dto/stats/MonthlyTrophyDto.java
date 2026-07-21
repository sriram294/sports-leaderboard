package com.org.playboard.dto.stats;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A month a player topped the group's leaderboard.
 *
 * <p>{@code month} is an ISO {@code YYYY-MM} string rather than a date: the day is
 * meaningless here, and sending a full date invites clients to render "1 July" when the
 * thing being described is the whole month.
 *
 * <p>Only awarded months are ever serialised — the null-winner rows the job writes to mark a
 * month decided are bookkeeping and never leave the backend.
 */
public record MonthlyTrophyDto(
        String month,
        UUID userId,
        String displayName,
        String photoUrl,
        String avatarId,
        String avatarColor,
        BigDecimal rating,
        Integer gamesPlayed,
        Integer wins) {}
