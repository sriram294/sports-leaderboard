package com.org.playboard.dto.stats;

import java.util.List;

public record LeaderboardResponse(List<LeaderboardEntryDto> rankings) {}
