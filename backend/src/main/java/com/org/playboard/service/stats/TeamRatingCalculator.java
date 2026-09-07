package com.org.playboard.service.stats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.UUID;

/** Pure, deterministic two-team TrueSkill-style update used by team-v1. */
public final class TeamRatingCalculator {
    public static final double INITIAL_MEAN = 25.0;
    public static final double INITIAL_SIGMA = 8.333333333333333;
    private static final double BETA = 4.166666666666667;

    private TeamRatingCalculator() {}

    public record Rating(double mean, double sigma) {
        public Rating() { this(INITIAL_MEAN, INITIAL_SIGMA); }
    }

    public record Participant(UUID userId, boolean guest) {}

    public record Team(List<Participant> players, boolean winner) {
        public Team {
            players = List.copyOf(players);
        }
    }

    /** Applies one valid two-team result. Guest ratings are read as priors and never returned. */
    public static Map<UUID, Rating> update(Map<UUID, Rating> before, List<Team> teams) {
        if (teams == null || teams.size() != 2 || teams.get(0).winner() == teams.get(1).winner()
                || teams.stream().anyMatch(t -> t.players().isEmpty())) {
            return before;
        }
        HashSet<UUID> seen = new HashSet<>();
        if (teams.stream().flatMap(t -> t.players().stream()).anyMatch(p -> p.userId() == null || !seen.add(p.userId()))) {
            return before;
        }
        Map<UUID, Rating> out = new LinkedHashMap<>(before);
        double m1 = strength(teams.get(0), before);
        double m2 = strength(teams.get(1), before);
        double v1 = variance(teams.get(0), before);
        double v2 = variance(teams.get(1), before);
        double c = Math.sqrt(2 * BETA * BETA + v1 + v2);
        double signedDiff = teams.get(0).winner() ? m1 - m2 : m2 - m1;
        double t = signedDiff / c;
        double p = normalCdf(t);
        if (p < 1e-12) p = 1e-12;
        double v = normalPdf(t) / p;
        double w = v * (v + t);
        for (Team team : teams) {
            double sign = team.winner() ? 1 : -1;
            for (Participant participant : team.players()) {
                if (participant.guest()) continue;
                Rating old = before.getOrDefault(participant.userId(), new Rating());
                double variance = old.sigma() * old.sigma();
                double mean = old.mean() + sign * variance / c * v;
                double sigmaSquared = variance * Math.max(1e-9, 1 - variance / (c * c) * w);
                out.put(participant.userId(), new Rating(mean, Math.sqrt(sigmaSquared)));
            }
        }
        return out;
    }

    public static double strength(Team team, Map<UUID, Rating> ratings) {
        return team.players().stream().mapToDouble(p -> ratings.getOrDefault(p.userId(), new Rating()).mean()).sum();
    }

    public static double variance(Team team, Map<UUID, Rating> ratings) {
        return team.players().stream().mapToDouble(p -> {
            // Guests are intentionally still part of team uncertainty/prediction.
            return ratings.getOrDefault(p.userId(), new Rating()).sigma() * ratings.getOrDefault(p.userId(), new Rating()).sigma();
        }).sum();
    }

    public static double conservativeScore(Rating rating) {
        return rating.mean() - 3 * rating.sigma();
    }

    // Abramowitz-Stegun approximation, sufficient for stable server-side ranking.
    private static double normalCdf(double x) {
        double sign = x < 0 ? -1 : 1;
        x = Math.abs(x) / Math.sqrt(2);
        double t = 1 / (1 + 0.3275911 * x);
        double y = 1 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
                - 0.284496736) * t + 0.254829592) * t * Math.exp(-x * x);
        return 0.5 * (1 + sign * y);
    }

    private static double normalPdf(double x) {
        return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI);
    }
}
