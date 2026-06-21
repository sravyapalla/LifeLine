package com.lifeline.simulation;

import com.lifeline.dispatch.DispatchEngine;
import com.lifeline.routing.StraightLineRoutingProvider;
import com.lifeline.store.InMemoryLifeLineStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SimulationServiceTest {
    @Test
    void runsDeterministicGreedySimulationAndPersistsHistory() {
        InMemoryLifeLineStore store = new InMemoryLifeLineStore();
        store.reset();
        SimulationService service = new SimulationService(
                store,
                new DispatchEngine(new StraightLineRoutingProvider(), 32, 28)
        );

        SimulationResult result = service.run(new SimulationRequest(
                5,
                42L,
                0.4,
                List.of(),
                List.of(),
                10,
                OptimizationStrategy.GREEDY_SEQUENTIAL
        ));

        assertThat(result.strategyResults()).hasSize(2);
        assertThat(result.strategyResults().getFirst().assignments()).hasSize(5);
        assertThat(result.strategyResults())
                .extracting(SimulationStrategyResult::strategy)
                .containsExactly(OptimizationStrategy.GREEDY_SEQUENTIAL, OptimizationStrategy.GLOBAL_MIN_COST);
        assertThat(service.simulations()).extracting(SimulationResult::id).contains(result.id());
    }

    @Test
    void reportsUnmatchedAssignmentsWhenAllHospitalsAreExhausted() {
        InMemoryLifeLineStore store = new InMemoryLifeLineStore();
        store.reset();
        SimulationService service = new SimulationService(
                store,
                new DispatchEngine(new StraightLineRoutingProvider(), 32, 28)
        );

        SimulationResult result = service.run(new SimulationRequest(
                3,
                99L,
                1.0,
                List.of(),
                List.of("HOS-201", "HOS-202", "HOS-203", "HOS-204", "HOS-205"),
                0,
                OptimizationStrategy.GREEDY_SEQUENTIAL
        ));

        assertThat(result.strategyResults().getFirst().unmatchedCount()).isEqualTo(3);
    }

    @Test
    void rejectsLargeExactOptimizationRequests() {
        InMemoryLifeLineStore store = new InMemoryLifeLineStore();
        store.reset();
        SimulationService service = new SimulationService(
                store,
                new DispatchEngine(new StraightLineRoutingProvider(), 32, 28)
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.run(new SimulationRequest(
                        13,
                        99L,
                        0.5,
                        List.of(),
                        List.of(),
                        0,
                        OptimizationStrategy.GLOBAL_MIN_COST
                )))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("capped at 12");
    }
}
