package com.org.playboard.dto.stats;

import java.util.List;

/**
 * A group's leaderboard.
 *
 * <p>{@code minGamesToRank} is a group-level fact — the games a player needs before they
 * rank rather than showing as provisional — so it lives here once instead of being repeated
 * on every entry. Clients derive "N more to rank" as {@code minGamesToRank - gamesPlayed}.
 */
public record LeaderboardResponse(List<LeaderboardEntryDto> rankings, int minGamesToRank) {}
