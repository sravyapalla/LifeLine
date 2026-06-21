package com.lifeline.location;

import com.lifeline.domain.AmbulanceLocationSnapshot;
import com.lifeline.domain.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Profile("memory")
public class InMemoryAmbulanceLocationProjection implements AmbulanceLocationProjection {
    private final Map<String, AmbulanceLocationSnapshot> snapshots = new LinkedHashMap<>();
    private final Duration ttl;

    public InMemoryAmbulanceLocationProjection(@Value("${lifeline.live-location.ttl-seconds:180}") long ttlSeconds) {
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Override
    public synchronized AmbulanceLocationSnapshot update(String ambulanceId, Location location, Instant updatedAt) {
        AmbulanceLocationSnapshot snapshot = new AmbulanceLocationSnapshot(
                ambulanceId,
                location,
                updatedAt,
                updatedAt.plus(ttl),
                "memory"
        );
        snapshots.put(ambulanceId, snapshot);
        return snapshot;
    }

    @Override
    public synchronized List<AmbulanceLocationSnapshot> snapshots() {
        pruneExpired();
        return snapshots.values().stream()
                .sorted(Comparator.comparing(AmbulanceLocationSnapshot::ambulanceId))
                .toList();
    }

    @Override
    public synchronized Optional<AmbulanceLocationSnapshot> find(String ambulanceId) {
        pruneExpired();
        return Optional.ofNullable(snapshots.get(ambulanceId));
    }

    @Override
    public synchronized void clear() {
        snapshots.clear();
    }

    private void pruneExpired() {
        Instant now = Instant.now();
        snapshots.entrySet().removeIf(entry -> !entry.getValue().isFresh(now));
    }
}
