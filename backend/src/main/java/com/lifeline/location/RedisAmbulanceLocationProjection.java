package com.lifeline.location;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeline.domain.AmbulanceLocationSnapshot;
import com.lifeline.domain.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@Profile("!memory")
public class RedisAmbulanceLocationProjection implements AmbulanceLocationProjection {
    private static final String KEY_PREFIX = "lifeline:ambulance:location:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public RedisAmbulanceLocationProjection(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${lifeline.live-location.ttl-seconds:180}") long ttlSeconds
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public AmbulanceLocationSnapshot update(String ambulanceId, Location location, Instant updatedAt) {
        AmbulanceLocationSnapshot snapshot = new AmbulanceLocationSnapshot(
                ambulanceId,
                location,
                updatedAt,
                updatedAt.plus(ttl),
                "redis"
        );
        redis.opsForValue().set(key(ambulanceId), toJson(snapshot), ttl);
        return snapshot;
    }

    @Override
    public List<AmbulanceLocationSnapshot> snapshots() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }
        Instant now = Instant.now();
        return keys.stream()
                .map(redis.opsForValue()::get)
                .flatMap(value -> fromJson(value).stream())
                .filter(snapshot -> snapshot.isFresh(now))
                .sorted(Comparator.comparing(AmbulanceLocationSnapshot::ambulanceId))
                .toList();
    }

    @Override
    public Optional<AmbulanceLocationSnapshot> find(String ambulanceId) {
        return fromJson(redis.opsForValue().get(key(ambulanceId)))
                .filter(snapshot -> snapshot.isFresh(Instant.now()));
    }

    @Override
    public void clear() {
        Set<String> keys = redis.keys(KEY_PREFIX + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private String key(String ambulanceId) {
        return KEY_PREFIX + ambulanceId;
    }

    private String toJson(AmbulanceLocationSnapshot snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize ambulance location.", exception);
        }
    }

    private Optional<AmbulanceLocationSnapshot> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, AmbulanceLocationSnapshot.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to read ambulance location.", exception);
        }
    }
}
