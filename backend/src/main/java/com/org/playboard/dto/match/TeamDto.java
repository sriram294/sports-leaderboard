package com.org.playboard.dto.match;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TeamDto(short teamNo, @JsonProperty("isWinner") boolean winner, List<PlayerRefDto> players) {}
