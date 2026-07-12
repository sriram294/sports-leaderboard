package com.org.playboard.dto.stats;

import java.math.BigDecimal;
import java.util.UUID;

public record LeaderboardEntryDto(
        int rank,
        UUID userId,
        String displayName,
        String photoUrl,
        String avatarColor,
        int gamesPlayed,
        int wins,
        int losses,
        int pointsFor,
        BigDecimal winRate,
        int currentStreak,
        int bestStreak) {}
