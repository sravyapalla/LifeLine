package com.lifeline.simulation;

import java.time.Instant;
import java.util.List;

public record SimulationResult(
        String id,
        SimulationRequest request,
        Instant createdAt,
        List<SimulationStrategyResult> strategyResults
) {
}
