package com.lifeline.domain;

import java.util.Set;

public record Hospital(
        String id,
        String name,
        Location location,
        Set<EmergencyCondition> specialties,
        int totalBeds,
        int availableBeds,
        double qualityScore
) {
    public boolean canTreat(EmergencyCondition condition) {
        return condition == EmergencyCondition.GENERAL || specialties.contains(condition);
    }

    public boolean hasCapacity() {
        return availableBeds > 0;
    }

    public double loadFactor() {
        if (totalBeds <= 0) {
            return 1;
        }
        return 1 - ((double) availableBeds / totalBeds);
    }

    public Hospital reserveOneBed() {
        return new Hospital(id, name, location, specialties, totalBeds, Math.max(availableBeds - 1, 0), qualityScore);
    }
}

