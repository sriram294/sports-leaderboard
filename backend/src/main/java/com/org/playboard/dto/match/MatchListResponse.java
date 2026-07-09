package com.org.playboard.dto.match;

import java.util.List;

public record MatchListResponse(List<MatchSummaryDto> matches, String nextCursor) {}
