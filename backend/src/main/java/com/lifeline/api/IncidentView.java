package com.lifeline.api;

import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.Incident;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.IncidentStatus;
import com.lifeline.domain.Location;

import java.time.Instant;

public record IncidentView(
        String id,
        String patientName,
        String phone,
        EmergencyCondition condition,
        IncidentPriority priority,
        Location location,
        String addressText,
        String landmark,
        String locationSource,
        Instant createdAt,
        IncidentStatus status
) {
    public static IncidentView full(Incident incident) {
        return new IncidentView(
                incident.id(),
                incident.patientName(),
                incident.phone(),
                incident.condition(),
                incident.priority(),
                incident.location(),
                incident.addressText(),
                incident.landmark(),
                incident.locationSource(),
                incident.createdAt(),
                incident.status()
        );
    }

    public static IncidentView operational(Incident incident) {
        return new IncidentView(
                incident.id(),
                "Incident " + incident.id(),
                maskPhone(incident.phone()),
                incident.condition(),
                incident.priority(),
                incident.location(),
                incident.addressText(),
                incident.landmark(),
                incident.locationSource(),
                incident.createdAt(),
                incident.status()
        );
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "****" + digits.substring(digits.length() - 4);
    }
}
