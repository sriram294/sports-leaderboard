package com.org.playboard.dto.match;

import java.math.BigDecimal;
import java.util.UUID;

/** One participant's signed change in the active leaderboard's displayed rating. */
public record RatingChangeDto(UUID userId, BigDecimal ratingDelta) {}
