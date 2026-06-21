package com.lifeline.simulation;

import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.IncidentPriority;
import com.lifeline.domain.Location;

public record SimulationAssignment(
        OptimizationStrategy strategy,
        String incidentId,
        EmergencyCondition condition,
        IncidentPriority priority,
        Location incidentLocation,
        String ambulanceId,
        String hospitalId,
        double pickupEtaMinutes,
        double hospitalEtaMinutes,
        double totalCost,
        boolean matched,
        String reason
) {
}
