package com.lifeline.simulation;

import com.lifeline.dispatch.DispatchDecision;
import com.lifeline.dispatch.DispatchEngine;
import com.lifeline.dispatch.CandidateScore;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
                request.strategy()
        );
        if (normalized.strategy() == OptimizationStrategy.GLOBAL_MIN_COST && normalized.incidentCount() > 12) {
            throw new IllegalStateException("GLOBAL_MIN_COST simulations are capped at 12 incidents.");
        }
        List<Incident> incidents = generateIncidents(normalized);
        List<Ambulance> ambulances = prepareAmbulances(normalized);
        List<Hospital> hospitals = prepareHospitals(normalized);
        List<SimulationAssignment> greedyAssignments = greedyAssignments(
                incidents,
                ambulances,
                hospitals,
                OptimizationStrategy.GREEDY_SEQUENTIAL
        );
        SimulationStrategyResult greedy = summarize(OptimizationStrategy.GREEDY_SEQUENTIAL, greedyAssignments, 0);
        List<SimulationStrategyResult> strategyResults = new ArrayList<>();
        strategyResults.add(greedy);
        if (normalized.incidentCount() <= 12) {
            List<SimulationAssignment> globalAssignments = globalMinCostAssignments(incidents, ambulances, hospitals);
            double improvementPercent = improvementPercent(greedy.totalCost(), totalCost(globalAssignments));
            strategyResults.add(summarize(OptimizationStrategy.GLOBAL_MIN_COST, globalAssignments, improvementPercent));
        }
        SimulationResult result = new SimulationResult(
                "SIM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                normalized,
                Instant.now(),
                strategyResults
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

    List<SimulationAssignment> globalMinCostAssignments(
            List<Incident> incidents,
            List<Ambulance> ambulances,
            List<Hospital> hospitals
    ) {
        if (incidents.size() > 12) {
            throw new IllegalStateException("GLOBAL_MIN_COST simulations are capped at 12 incidents.");
        }

        Map<String, Integer> hospitalCapacity = new HashMap<>();
        for (Hospital hospital : hospitals) {
            hospitalCapacity.put(hospital.id(), hospital.availableBeds());
        }
        List<List<CandidateScore>> candidatesByIncident = incidents.stream()
                .map(incident -> dispatchEngine.scoreCandidates(incident, ambulances, hospitals))
                .toList();

        SearchState best = new SearchState(List.of(), -1, Double.MAX_VALUE);
        best = searchGlobal(
                incidents,
                candidatesByIncident,
                hospitalCapacity,
                new HashSet<>(),
                new ArrayList<>(),
                0,
                0,
                0,
                best
        );
        return best.assignments();
    }

    private SearchState searchGlobal(
            List<Incident> incidents,
            List<List<CandidateScore>> candidatesByIncident,
            Map<String, Integer> hospitalCapacity,
            Set<String> usedAmbulances,
            List<SimulationAssignment> currentAssignments,
            int index,
            int matchedCount,
            double totalCost,
            SearchState best
    ) {
        if (index == incidents.size()) {
            if (matchedCount > best.matchedCount()
                    || (matchedCount == best.matchedCount() && totalCost < best.totalCost())) {
                return new SearchState(List.copyOf(currentAssignments), matchedCount, totalCost);
            }
            return best;
        }

        Incident incident = incidents.get(index);
        for (CandidateScore candidate : candidatesByIncident.get(index)) {
            if (usedAmbulances.contains(candidate.ambulanceId())
                    || hospitalCapacity.getOrDefault(candidate.hospitalId(), 0) <= 0) {
                continue;
            }
            usedAmbulances.add(candidate.ambulanceId());
            hospitalCapacity.put(candidate.hospitalId(), hospitalCapacity.get(candidate.hospitalId()) - 1);
            currentAssignments.add(matchedAssignment(OptimizationStrategy.GLOBAL_MIN_COST, incident, candidate));
            best = searchGlobal(
                    incidents,
                    candidatesByIncident,
                    hospitalCapacity,
                    usedAmbulances,
                    currentAssignments,
                    index + 1,
                    matchedCount + 1,
                    totalCost + candidate.totalCost(),
                    best
            );
            currentAssignments.removeLast();
            hospitalCapacity.put(candidate.hospitalId(), hospitalCapacity.get(candidate.hospitalId()) + 1);
            usedAmbulances.remove(candidate.ambulanceId());
        }

        currentAssignments.add(unmatched(OptimizationStrategy.GLOBAL_MIN_COST, incident, "No globally compatible ambulance and hospital pair."));
        best = searchGlobal(
                incidents,
                candidatesByIncident,
                hospitalCapacity,
                usedAmbulances,
                currentAssignments,
                index + 1,
                matchedCount,
                totalCost,
                best
        );
        currentAssignments.removeLast();
        return best;
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
                    "simulation",
                    "Sim Patient " + (index + 1),
                    "+91-90000-SIM",
                    condition,
                    priority,
                    new Location(round(latitude), round(longitude)),
                    "Generated scenario address " + (index + 1),
                    "",
                    "SIMULATION",
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

    private SimulationAssignment matchedAssignment(
            OptimizationStrategy strategy,
            Incident incident,
            CandidateScore candidate
    ) {
        return new SimulationAssignment(
                strategy,
                incident.id(),
                incident.condition(),
                incident.priority(),
                incident.location(),
                candidate.ambulanceId(),
                candidate.hospitalId(),
                candidate.pickupEtaMinutes(),
                candidate.hospitalEtaMinutes(),
                candidate.totalCost(),
                true,
                candidate.explanation()
        );
    }

    private double totalCost(List<SimulationAssignment> assignments) {
        return assignments.stream()
                .filter(SimulationAssignment::matched)
                .mapToDouble(SimulationAssignment::totalCost)
                .sum();
    }

    private double improvementPercent(double baselineCost, double optimizedCost) {
        if (baselineCost <= 0) {
            return 0;
        }
        return Math.max(0, ((baselineCost - optimizedCost) / baselineCost) * 100);
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private record SearchState(
            List<SimulationAssignment> assignments,
            int matchedCount,
            double totalCost
    ) {
    }
}
