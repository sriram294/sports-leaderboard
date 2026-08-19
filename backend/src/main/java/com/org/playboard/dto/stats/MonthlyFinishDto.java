package com.org.playboard.dto.stats;

/** A frozen finishing position for one captured completed month. */
public record MonthlyFinishDto(String month, Integer rank, int qualifiedPlayers) {}
