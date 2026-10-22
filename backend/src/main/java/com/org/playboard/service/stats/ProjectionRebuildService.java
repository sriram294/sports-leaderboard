package com.org.playboard.service.stats;

import com.org.playboard.dto.stats.LeaderboardEntryDto;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Rebuilds one month at a time; expensive history scans never run on a match-write thread. */
@Service
@RegisterReflectionForBinding(LeaderboardEntryDto.class)
public class ProjectionRebuildService {
    private final JdbcTemplate jdbc;
    private final ObjectProvider<StatsQueryService> stats;
    private final ObjectMapper json;

    public ProjectionRebuildService(JdbcTemplate jdbc, ObjectProvider<StatsQueryService> stats, ObjectMapper json) {
        this.jdbc = jdbc;
        this.stats = stats;
        this.json = json;
    }

    @Transactional
    public void markRebuilding(UUID groupId, Instant from, Instant to) {
        jdbc.update("""
            insert into group_period_projection (group_id, period_start, period_end, algorithm_version, source_version, status)
            select id, ?, ?, 'team-v2', leaderboard_version, 'rebuilding' from groups where id = ?
            on conflict (group_id, period_start, period_end, algorithm_version) do update
              set source_version = excluded.source_version, status = 'rebuilding', requested_at = now(), failure_message = null
            """, timestamptz(from), timestamptz(to), groupId);
    }

    @Transactional
    public void rebuild(UUID groupId, Instant from, Instant to) {
        try {
            var standings = stats.getObject().rankedStandings(groupId, from, to, null);
            String payload = json.writeValueAsString(standings.entries());
            jdbc.update("""
                update group_period_projection set status = 'ready', standings = cast(? as jsonb), rebuilt_at = now(), failure_message = null
                where group_id = ? and period_start = ? and period_end = ? and algorithm_version = 'team-v2'
                """, payload, groupId, timestamptz(from), timestamptz(to));
        } catch (Exception exception) {
            jdbc.update("""
                update group_period_projection set status = 'failed', failure_message = ?
                where group_id = ? and period_start = ? and period_end = ? and algorithm_version = 'team-v2'
                """, exception.getMessage(), groupId, timestamptz(from), timestamptz(to));
        }
    }

    private SqlParameterValue timestamptz(Instant instant) {
        return new SqlParameterValue(Types.TIMESTAMP_WITH_TIMEZONE, OffsetDateTime.ofInstant(instant, ZoneOffset.UTC));
    }
}
