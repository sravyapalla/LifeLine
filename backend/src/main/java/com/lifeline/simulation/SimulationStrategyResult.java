package com.lifeline.simulation;

import java.util.List;

public record SimulationStrategyResult(
        OptimizationStrategy strategy,
        int matchedCount,
        int unmatchedCount,
        double averagePickupEtaMinutes,
        double averageTransferEtaMinutes,
        double totalCost,
        double improvementPercent,
        List<SimulationAssignment> assignments
) {
}
