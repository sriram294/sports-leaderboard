package com.org.playboard.dto.stats;

import java.math.BigDecimal;
import java.util.UUID;

public record BestPartnerDto(
        UUID userId, String displayName, String avatarColor, int gamesTogether, int winsTogether, BigDecimal winRate) {}
