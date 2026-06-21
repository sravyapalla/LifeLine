package com.lifeline.simulation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

public record SimulationRequest(
        @Min(1) @Max(40) int incidentCount,
        Long randomSeed,
        @DecimalMin("0.0") @DecimalMax("1.0") Double criticalRatio,
        List<String> ambulanceOutages,
        List<String> exhaustedHospitals,
        @Min(0) @Max(100) int capacityStressPercent,
        OptimizationStrategy strategy
) {
    public SimulationRequest {
        if (incidentCount <= 0) {
            incidentCount = 6;
        }
        if (randomSeed == null) {
            randomSeed = 20260621L;
        }
        if (criticalRatio == null) {
            criticalRatio = 0.35;
        }
        ambulanceOutages = ambulanceOutages == null ? List.of() : List.copyOf(ambulanceOutages);
        exhaustedHospitals = exhaustedHospitals == null ? List.of() : List.copyOf(exhaustedHospitals);
        strategy = strategy == null ? OptimizationStrategy.GREEDY_SEQUENTIAL : strategy;
    }
}
