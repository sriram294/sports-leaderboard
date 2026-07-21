package com.org.playboard.dto.stats;

import com.org.playboard.dto.match.MatchSummaryDto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Backs both the Profile tab (own stats) and tapping a player from the leaderboard — same shape either way. */
public record PlayerStatsDto(
        UUID userId,
        String displayName,
        String photoUrl,
        String avatarId,
        String avatarColor,
        int matchesPlayed,
        int wins,
        int losses,
        int pointsFor,
        int pointsAgainst,
        BigDecimal winRate,
        int currentStreak,
        int bestStreak,
        BestPartnerDto bestPartner,
        List<MatchSummaryDto> recentMatches,
        /** Months this player topped the group, newest first; empty for everyone else. */
        List<MonthlyTrophyDto> trophies) {}
