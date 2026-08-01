package com.org.playboard.dto.stats;

import java.math.BigDecimal;
import java.util.UUID;

public record PartnerDto(
        UUID userId,
        String displayName,
        String avatarId,
        String avatarColor,
        int gamesTogether,
        int winsTogether,
        BigDecimal winRate) {}
