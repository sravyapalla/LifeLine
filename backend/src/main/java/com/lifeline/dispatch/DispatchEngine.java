package com.lifeline.dispatch;

import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceStatus;
import com.lifeline.domain.AmbulanceType;
import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.routing.RouteEstimate;
import com.lifeline.routing.RoutingProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class DispatchEngine {
    private final double ambulanceSpeedKmph;
    private final double transferSpeedKmph;
    private final RoutingProvider routingProvider;

    public DispatchEngine(
            RoutingProvider routingProvider,
            @Value("${lifeline.dispatch.average-ambulance-speed-kmph:32}") double ambulanceSpeedKmph,
            @Value("${lifeline.dispatch.average-transfer-speed-kmph:28}") double transferSpeedKmph
    ) {
        this.routingProvider = routingProvider;
        this.ambulanceSpeedKmph = ambulanceSpeedKmph;
        this.transferSpeedKmph = transferSpeedKmph;
    }

    public DispatchDecision decide(Incident incident, List<Ambulance> ambulances, List<Hospital> hospitals) {
        List<CandidateScore> scores = scoreCandidates(incident, ambulances, hospitals);

        if (scores.isEmpty()) {
            throw new NoDispatchCandidateException("No compatible ambulance and hospital pair is available.");
        }

        CandidateScore winner = scores.getFirst();
        Ambulance ambulance = ambulances.stream()
                .filter(candidate -> candidate.id().equals(winner.ambulanceId()))
                .findFirst()
                .orElseThrow();
        Hospital hospital = hospitals.stream()
                .filter(candidate -> candidate.id().equals(winner.hospitalId()))
                .findFirst()
                .orElseThrow();

        return new DispatchDecision(ambulance, hospital, winner, scores.stream().limit(8).toList());
    }

    public DispatchDecision rerouteHospital(Incident incident, Ambulance ambulance, String currentHospitalId, List<Hospital> hospitals) {
        AmbulanceType requiredType = requiredAmbulanceType(incident.condition(), incident.priority());
        if (!ambulance.type().canHandle(requiredType)) {
            throw new NoDispatchCandidateException("Assigned ambulance can no longer satisfy this incident.");
        }

        List<Hospital> eligibleHospitals = hospitals.stream()
                .filter(hospital -> !hospital.id().equals(currentHospitalId))
                .filter(Hospital::hasCapacity)
                .filter(hospital -> hospital.canTreat(incident.condition()))
                .toList();

        List<CandidateScore> scores = eligibleHospitals.stream()
                .map(hospital -> score(incident, ambulance, hospital, requiredType))
                .sorted(Comparator.comparingDouble(CandidateScore::totalCost))
                .toList();

        if (scores.isEmpty()) {
            throw new NoDispatchCandidateException("No alternate hospital is available for reroute.");
        }

        CandidateScore winner = scores.getFirst();
        Hospital hospital = eligibleHospitals.stream()
                .filter(candidate -> candidate.id().equals(winner.hospitalId()))
                .findFirst()
                .orElseThrow();

        return new DispatchDecision(ambulance, hospital, winner, scores.stream().limit(8).toList());
    }

    public List<CandidateScore> scoreCandidates(Incident incident, List<Ambulance> ambulances, List<Hospital> hospitals) {
        AmbulanceType requiredType = requiredAmbulanceType(incident.condition(), incident.priority());

        List<Ambulance> eligibleAmbulances = ambulances.stream()
                .filter(ambulance -> ambulance.status() == AmbulanceStatus.AVAILABLE)
                .filter(ambulance -> ambulance.type().canHandle(requiredType))
                .toList();

        List<Hospital> eligibleHospitals = hospitals.stream()
                .filter(Hospital::hasCapacity)
                .filter(hospital -> hospital.canTreat(incident.condition()))
                .toList();

        return eligibleAmbulances.stream()
                .flatMap(ambulance -> eligibleHospitals.stream()
                        .map(hospital -> score(incident, ambulance, hospital, requiredType)))
                .sorted(Comparator.comparingDouble(CandidateScore::totalCost))
                .toList();
    }

    private CandidateScore score(Incident incident, Ambulance ambulance, Hospital hospital, AmbulanceType requiredType) {
        RouteEstimate pickupEstimate = routingProvider.estimate(ambulance.location(), incident.location(), ambulanceSpeedKmph);
        RouteEstimate hospitalEstimate = routingProvider.estimate(incident.location(), hospital.location(), transferSpeedKmph);
        double pickupEtaMinutes = pickupEstimate.etaMinutes();
        double hospitalEtaMinutes = hospitalEstimate.etaMinutes();
        double loadPenalty = hospital.loadFactor() * 9;
        double qualityPenalty = (1 - hospital.qualityScore()) * 12;
        double typePenalty = Math.max(ambulance.type().level() - requiredType.level(), 0) * 1.2;
        double totalCost = pickupEtaMinutes * incident.priority().pickupUrgencyWeight()
                + hospitalEtaMinutes * 0.85
                + loadPenalty
                + qualityPenalty
                + typePenalty;

        String explanation = "pickup %.1f min, transfer %.1f min, load %.0f%%, quality %.2f"
                .formatted(pickupEtaMinutes, hospitalEtaMinutes, hospital.loadFactor() * 100, hospital.qualityScore());

        return new CandidateScore(
                ambulance.id(),
                hospital.id(),
                round(pickupEtaMinutes),
                round(hospitalEtaMinutes),
                round(hospital.loadFactor()),
                round(qualityPenalty),
                round(typePenalty),
                round(totalCost),
                explanation
        );
    }

    private AmbulanceType requiredAmbulanceType(EmergencyCondition condition, IncidentPriority priority) {
        if (priority == IncidentPriority.CRITICAL) {
            return AmbulanceType.ALS;
        }
        return switch (condition) {
            case CARDIAC, STROKE -> AmbulanceType.ALS;
            case TRAUMA -> priority == IncidentPriority.HIGH ? AmbulanceType.ALS : AmbulanceType.BLS;
            case PEDIATRIC, GENERAL -> AmbulanceType.BLS;
        };
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
