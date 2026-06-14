package com.lifeline.dispatch;

import com.lifeline.domain.Ambulance;
import com.lifeline.domain.AmbulanceStatus;
import com.lifeline.domain.AmbulanceType;
import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.Hospital;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.Location;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class DispatchEngine {
    private final double ambulanceSpeedKmph;
    private final double transferSpeedKmph;

    public DispatchEngine(
            @Value("${lifeline.dispatch.average-ambulance-speed-kmph:32}") double ambulanceSpeedKmph,
            @Value("${lifeline.dispatch.average-transfer-speed-kmph:28}") double transferSpeedKmph
    ) {
        this.ambulanceSpeedKmph = ambulanceSpeedKmph;
        this.transferSpeedKmph = transferSpeedKmph;
    }

    public DispatchDecision decide(Incident incident, List<Ambulance> ambulances, List<Hospital> hospitals) {
        AmbulanceType requiredType = requiredAmbulanceType(incident.condition(), incident.priority());

        List<Ambulance> eligibleAmbulances = ambulances.stream()
                .filter(ambulance -> ambulance.status() == AmbulanceStatus.AVAILABLE)
                .filter(ambulance -> ambulance.type().canHandle(requiredType))
                .toList();

        List<Hospital> eligibleHospitals = hospitals.stream()
                .filter(Hospital::hasCapacity)
                .filter(hospital -> hospital.canTreat(incident.condition()))
                .toList();

        List<CandidateScore> scores = eligibleAmbulances.stream()
                .flatMap(ambulance -> eligibleHospitals.stream()
                        .map(hospital -> score(incident, ambulance, hospital, requiredType)))
                .sorted(Comparator.comparingDouble(CandidateScore::totalCost))
                .toList();

        if (scores.isEmpty()) {
            throw new NoDispatchCandidateException("No compatible ambulance and hospital pair is available.");
        }

        CandidateScore winner = scores.getFirst();
        Ambulance ambulance = eligibleAmbulances.stream()
                .filter(candidate -> candidate.id().equals(winner.ambulanceId()))
                .findFirst()
                .orElseThrow();
        Hospital hospital = eligibleHospitals.stream()
                .filter(candidate -> candidate.id().equals(winner.hospitalId()))
                .findFirst()
                .orElseThrow();

        return new DispatchDecision(ambulance, hospital, winner, scores.stream().limit(8).toList());
    }

    private CandidateScore score(Incident incident, Ambulance ambulance, Hospital hospital, AmbulanceType requiredType) {
        double pickupDistanceKm = distanceKm(ambulance.location(), incident.location());
        double hospitalDistanceKm = distanceKm(incident.location(), hospital.location());
        double pickupEtaMinutes = minutesAtSpeed(pickupDistanceKm, ambulanceSpeedKmph);
        double hospitalEtaMinutes = minutesAtSpeed(hospitalDistanceKm, transferSpeedKmph);
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

    private double minutesAtSpeed(double distanceKm, double kmph) {
        if (kmph <= 0) {
            return Double.MAX_VALUE;
        }
        return distanceKm / kmph * 60;
    }

    private double distanceKm(Location from, Location to) {
        double earthRadiusKm = 6371;
        double dLat = Math.toRadians(to.latitude() - from.latitude());
        double dLng = Math.toRadians(to.longitude() - from.longitude());
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}

