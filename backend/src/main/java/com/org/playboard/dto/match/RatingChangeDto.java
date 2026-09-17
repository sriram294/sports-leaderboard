package com.org.playboard.dto.match;

import java.math.BigDecimal;
import java.util.UUID;

/** One participant's signed two-decimal change in the active leaderboard rating display. */
public record RatingChangeDto(UUID userId, BigDecimal ratingDelta) {}
