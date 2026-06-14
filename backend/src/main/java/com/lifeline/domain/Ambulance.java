package com.lifeline.domain;

public record Ambulance(
        String id,
        String callSign,
        AmbulanceType type,
        AmbulanceStatus status,
        Location location,
        String baseStation
) {
    public Ambulance withStatus(AmbulanceStatus nextStatus) {
        return new Ambulance(id, callSign, type, nextStatus, location, baseStation);
    }
}

