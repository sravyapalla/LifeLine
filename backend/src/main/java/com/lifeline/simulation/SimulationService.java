package com.lifeline.simulation;

import com.lifeline.dispatch.DispatchDecision;
import com.lifeline.dispatch.DispatchEngine;
import com.lifeline.dispatch.NoDispatchCandidateException;
import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceStatus;
import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.IncidentStatus;
import com.lifeline.domain.Location;
import com.lifeline.store.LifeLineStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Service
public class SimulationService {
    private static final Location BENGALURU_CENTER = new Location(12.9716, 77.5946);

    private final LifeLineStore store;
    private final DispatchEngine dispatchEngine;

    public SimulationService(LifeLineStore store, DispatchEngine dispatchEngine) {
        this.store = store;
        this.dispatchEngine = dispatchEngine;
    }

    public SimulationResult run(SimulationRequest request) {
        SimulationRequest normalized = new SimulationRequest(
                request.incidentCount(),
                request.randomSeed(),
                request.criticalRatio(),
                request.ambulanceOutages(),
                request.exhaustedHospitals(),
                request.capacityStressPercent(),
                OptimizationStrategy.GREEDY_SEQUENTIAL
        );
        List<Incident> incidents = generateIncidents(normalized);
        List<SimulationAssignment> assignments = greedyAssignments(
                incidents,
                prepareAmbulances(normalized),
                prepareHospitals(normalized),
                OptimizationStrategy.GREEDY_SEQUENTIAL
        );
        SimulationStrategyResult greedy = summarize(OptimizationStrategy.GREEDY_SEQUENTIAL, assignments, 0);
        SimulationResult result = new SimulationResult(
                "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                normalized,
                Instant.now(),
                List.of(greedy)
        );
        return store.saveSimulationResult(result);
    }

    public List<SimulationResult> simulations() {
        return store.simulations();
    }

    public SimulationResult simulation(String id) {
        return store.findSimulation(id)
                .orElseThrow(() -> new IllegalStateException("Simulation not found."));
    }

    List<SimulationAssignment> greedyAssignments(
            List<Incident> incidents,
            List<Ambulance> ambulances,
            List<Hospital> hospitals,
            OptimizationStrategy strategy
    ) {
        List<Ambulance> mutableAmbulances = new ArrayList<>(ambulances);
        List<Hospital> mutableHospitals = new ArrayList<>(hospitals);
        List<SimulationAssignment> assignments = new ArrayList<>();

        for (Incident incident : incidents) {
            try {
                DispatchDecision decision = dispatchEngine.decide(incident, mutableAmbulances, mutableHospitals);
                assignments.add(new SimulationAssignment(
                        strategy,
                        incident.id(),
                        incident.condition(),
                        incident.priority(),
                        incident.location(),
                        decision.ambulance().id(),
                        decision.hospital().id(),
                        decision.winningScore().pickupEtaMinutes(),
                        decision.winningScore().hospitalEtaMinutes(),
                        decision.winningScore().totalCost(),
                        true,
                        decision.winningScore().explanation()
                ));
                mutableAmbulances = mutableAmbulances.stream()
                        .map(ambulance -> ambulance.id().equals(decision.ambulance().id())
                                ? ambulance.withStatus(AmbulanceStatus.RESERVED)
                                : ambulance)
                        .toList();
                mutableHospitals = mutableHospitals.stream()
                        .map(hospital -> hospital.id().equals(decision.hospital().id())
                                ? hospital.reserveOneBed()
                                : hospital)
                        .toList();
            } catch (NoDispatchCandidateException exception) {
                assignments.add(unmatched(strategy, incident, exception.getMessage()));
            }
        }

        return assignments;
    }

    SimulationStrategyResult summarize(
            OptimizationStrategy strategy,
            List<SimulationAssignment> assignments,
            double improvementPercent
    ) {
        List<SimulationAssignment> matched = assignments.stream()
                .filter(SimulationAssignment::matched)
                .toList();
        double pickupAverage = matched.stream()
                .mapToDouble(SimulationAssignment::pickupEtaMinutes)
                .average()
                .orElse(0);
        double transferAverage = matched.stream()
                .mapToDouble(SimulationAssignment::hospitalEtaMinutes)
                .average()
                .orElse(0);
        double totalCost = matched.stream()
                .mapToDouble(SimulationAssignment::totalCost)
                .sum();
        return new SimulationStrategyResult(
                strategy,
                matched.size(),
                assignments.size() - matched.size(),
                round(pickupAverage),
                round(transferAverage),
                round(totalCost),
                round(improvementPercent),
                assignments
        );
    }

    private List<Ambulance> prepareAmbulances(SimulationRequest request) {
        return store.ambulances().stream()
                .map(ambulance -> request.ambulanceOutages().contains(ambulance.id())
                        ? ambulance.withStatus(AmbulanceStatus.OFFLINE)
                        : ambulance)
                .sorted(Comparator.comparing(Ambulance::id))
                .toList();
    }

    private List<Hospital> prepareHospitals(SimulationRequest request) {
        return store.hospitals().stream()
                .map(hospital -> {
                    if (request.exhaustedHospitals().contains(hospital.id())) {
                        return hospital.withAvailableBeds(0);
                    }
                    int stressedBeds = (int) Math.floor(hospital.availableBeds() * ((100.0 - request.capacityStressPercent()) / 100.0));
                    return hospital.withAvailableBeds(Math.max(0, stressedBeds));
                })
                .sorted(Comparator.comparing(Hospital::id))
                .toList();
    }

    private List<Incident> generateIncidents(SimulationRequest request) {
        Random random = new Random(request.randomSeed());
        List<Incident> incidents = new ArrayList<>();
        EmergencyCondition[] conditions = EmergencyCondition.values();

        for (int index = 0; index < request.incidentCount(); index += 1) {
            EmergencyCondition condition = conditions[random.nextInt(conditions.length)];
            IncidentPriority priority = random.nextDouble() < request.criticalRatio()
                    ? IncidentPriority.CRITICAL
                    : priorityFromRandom(random);
            double latitude = BENGALURU_CENTER.latitude() + ((random.nextDouble() - 0.5) * 0.15);
            double longitude = BENGALURU_CENTER.longitude() + ((random.nextDouble() - 0.5) * 0.18);
            incidents.add(new Incident(
                    "SIM-INC-" + (index + 1),
                    "Sim Patient " + (index + 1),
                    "+91-90000-SIM",
                    condition,
                    priority,
                    new Location(round(latitude), round(longitude)),
                    Instant.now(),
                    IncidentStatus.NEW
            ));
        }

        return incidents;
    }

    private IncidentPriority priorityFromRandom(Random random) {
        return switch (random.nextInt(3)) {
            case 0 -> IncidentPriority.MEDIUM;
            case 1 -> IncidentPriority.HIGH;
            default -> IncidentPriority.LOW;
        };
    }

    private SimulationAssignment unmatched(OptimizationStrategy strategy, Incident incident, String reason) {
        return new SimulationAssignment(
                strategy,
                incident.id(),
                incident.condition(),
                incident.priority(),
                incident.location(),
                null,
                null,
                0,
                0,
                0,
                false,
                reason
        );
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
