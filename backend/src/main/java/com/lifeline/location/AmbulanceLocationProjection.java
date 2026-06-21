package com.lifeline.location;

import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceLocationSnapshot;
import com.lifeline.domain.Location;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AmbulanceLocationProjection {
    AmbulanceLocationSnapshot update(String ambulanceId, Location location, Instant updatedAt);

    List<AmbulanceLocationSnapshot> snapshots();

    Optional<AmbulanceLocationSnapshot> find(String ambulanceId);

    void clear();

    default List<Ambulance> applyTo(List<Ambulance> ambulances) {
        return ambulances.stream()
                .map(this::applyTo)
                .toList();
    }

    default Ambulance applyTo(Ambulance ambulance) {
        return find(ambulance.id())
                .filter(snapshot -> snapshot.isFresh(Instant.now()))
                .map(snapshot -> ambulance.withLocation(snapshot.location()))
                .orElse(ambulance);
    }
}
