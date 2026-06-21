package com.lifeline.dispatch;

import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceStatus;
import com.lifeline.domain.AmbulanceType;
import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.IncidentStatus;
import com.lifeline.domain.Location;
import com.lifeline.routing.StraightLineRoutingProvider;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DispatchEngineTest {
    private final DispatchEngine engine = new DispatchEngine(new StraightLineRoutingProvider(), 32, 28);

    @Test
    void choosesCompatibleAmbulanceAndHospitalForCardiacIncident() {
        Incident incident = new Incident(
                "INC-1",
                "Patient",
                "+91-90000-00000",
                EmergencyCondition.CARDIAC,
                IncidentPriority.CRITICAL,
                new Location(12.95, 77.63),
                Instant.now(),
                IncidentStatus.NEW
        );

        DispatchDecision decision = engine.decide(
                incident,
                List.of(
                        new Ambulance("AMB-1", "Basic", AmbulanceType.BLS, AmbulanceStatus.AVAILABLE, new Location(12.951, 77.631), "Koramangala"),
                        new Ambulance("AMB-2", "Advanced", AmbulanceType.ALS, AmbulanceStatus.AVAILABLE, new Location(12.952, 77.632), "Indiranagar")
                ),
                List.of(
                        new Hospital("HOS-1", "General Hospital", new Location(12.96, 77.64), Set.of(EmergencyCondition.GENERAL), 30, 10, 0.8),
                        new Hospital("HOS-2", "Cardiac Hospital", new Location(12.955, 77.635), Set.of(EmergencyCondition.CARDIAC), 30, 10, 0.95)
                )
        );

        assertThat(decision.ambulance().id()).isEqualTo("AMB-2");
        assertThat(decision.hospital().id()).isEqualTo("HOS-2");
    }

    @Test
    void rerouteChoosesAlternateHospitalAndExcludesCurrentHospital() {
        Incident incident = new Incident(
                "INC-2",
                "Patient",
                "+91-90000-00000",
                EmergencyCondition.TRAUMA,
                IncidentPriority.HIGH,
                new Location(12.95, 77.63),
                Instant.now(),
                IncidentStatus.ASSIGNED
        );
        Ambulance ambulance = new Ambulance(
                "AMB-2",
                "Advanced",
                AmbulanceType.ALS,
                AmbulanceStatus.ON_TRIP,
                new Location(12.952, 77.632),
                "Indiranagar"
        );

        DispatchDecision decision = engine.rerouteHospital(
                incident,
                ambulance,
                "HOS-1",
                List.of(
                        new Hospital("HOS-1", "Current Hospital", new Location(12.955, 77.635), Set.of(EmergencyCondition.TRAUMA), 30, 12, 0.95),
                        new Hospital("HOS-2", "Exhausted Hospital", new Location(12.956, 77.636), Set.of(EmergencyCondition.TRAUMA), 30, 0, 0.98),
                        new Hospital("HOS-3", "Alternate Hospital", new Location(12.960, 77.640), Set.of(EmergencyCondition.TRAUMA), 30, 8, 0.9)
                )
        );

        assertThat(decision.ambulance().id()).isEqualTo("AMB-2");
        assertThat(decision.hospital().id()).isEqualTo("HOS-3");
        assertThat(decision.alternatives()).extracting(CandidateScore::hospitalId).doesNotContain("HOS-1", "HOS-2");
    }
}
