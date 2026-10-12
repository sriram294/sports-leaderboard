package com.org.playboard.service.stats;

import com.org.playboard.dto.stats.LeaderboardResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Optional Redis read-through cache; PostgreSQL remains the source of truth on every failure. */
@Service
public class LeaderboardSnapshotCache {
    private static final Logger log = LoggerFactory.getLogger(LeaderboardSnapshotCache.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final boolean enabled;
    private final Duration ttl;

    public LeaderboardSnapshotCache(StringRedisTemplate redis, ObjectMapper json,
            @Value("${playboard.leaderboard-cache.enabled:false}") boolean enabled,
            @Value("${playboard.leaderboard-cache.ttl:PT5M}") Duration ttl) {
        this.redis = redis;
        this.json = json;
        this.enabled = enabled;
        this.ttl = ttl;
    }

    public Optional<LeaderboardResponse> get(UUID groupId, String period, String algorithm, long version) {
        if (!enabled) return Optional.empty();
        try {
            String value = redis.opsForValue().get(key(groupId, period, algorithm, version));
            return value == null ? Optional.empty() : Optional.of(json.readValue(value, LeaderboardResponse.class));
        } catch (Exception exception) {
            log.warn("Leaderboard cache read failed for group {}; falling back to PostgreSQL.", groupId);
            return Optional.empty();
        }
    }

    public void put(UUID groupId, String period, String algorithm, long version, LeaderboardResponse response) {
        if (!enabled) return;
        try {
            redis.opsForValue().set(key(groupId, period, algorithm, version), json.writeValueAsString(response), ttl);
        } catch (Exception exception) {
            log.warn("Leaderboard cache write failed for group {}; response was still served.", groupId);
        }
    }

    private String key(UUID groupId, String period, String algorithm, long version) {
        return "leaderboard:" + groupId + ":" + period + ":" + algorithm + ":" + version;
    }
}
