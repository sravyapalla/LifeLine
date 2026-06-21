package com.lifeline.location;

import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceStatus;
import com.lifeline.domain.AmbulanceType;
import com.lifeline.domain.Location;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryAmbulanceLocationProjectionTest {
    @Test
    void overlaysFreshLocationOnAmbulance() {
        InMemoryAmbulanceLocationProjection projection = new InMemoryAmbulanceLocationProjection(180);
        Ambulance ambulance = new Ambulance(
                "AMB-1",
                "Alpha",
                AmbulanceType.ALS,
                AmbulanceStatus.AVAILABLE,
                new Location(12.0, 77.0),
                "Base"
        );

        projection.update("AMB-1", new Location(12.5, 77.5), Instant.now());

        List<Ambulance> result = projection.applyTo(List.of(ambulance));

        assertThat(result.getFirst().location()).isEqualTo(new Location(12.5, 77.5));
        assertThat(projection.snapshots()).hasSize(1);
    }
}
