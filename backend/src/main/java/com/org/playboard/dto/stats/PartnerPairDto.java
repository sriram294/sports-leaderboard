package com.org.playboard.dto.stats;

import java.math.BigDecimal;
import java.util.UUID;

/** One pair of players who have partnered together at least once in the group. */
public record PartnerPairDto(
        UUID player1Id,
        String player1DisplayName,
        String player1AvatarId,
        String player1AvatarColor,
        UUID player2Id,
        String player2DisplayName,
        String player2AvatarId,
        String player2AvatarColor,
        int gamesTogether,
        int winsTogether,
        BigDecimal winRate) {}
