package com.lifeline.domain;

public enum IncidentPriority {
    LOW(1.0),
    MEDIUM(1.15),
    HIGH(1.35),
    CRITICAL(1.65);

    private final double pickupUrgencyWeight;

    IncidentPriority(double pickupUrgencyWeight) {
        this.pickupUrgencyWeight = pickupUrgencyWeight;
    }

    public double pickupUrgencyWeight() {
        return pickupUrgencyWeight;
    }
}

