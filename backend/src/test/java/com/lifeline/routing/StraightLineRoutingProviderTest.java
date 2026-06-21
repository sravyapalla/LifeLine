package com.lifeline.routing;

import com.lifeline.domain.Location;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StraightLineRoutingProviderTest {
    private final StraightLineRoutingProvider provider = new StraightLineRoutingProvider();

    @Test
    void estimatesEtaFromStraightLineDistanceAndAverageSpeed() {
        RouteEstimate estimate = provider.estimate(
                new Location(12.9719, 77.6412),
                new Location(12.9458, 77.6309),
                30
        );

        assertThat(estimate.distanceKm()).isGreaterThan(2.5).isLessThan(3.5);
        assertThat(estimate.etaMinutes()).isGreaterThan(5).isLessThan(8);
    }

    @Test
    void protectsAgainstInvalidAverageSpeed() {
        RouteEstimate estimate = provider.estimate(
                new Location(12.9719, 77.6412),
                new Location(12.9458, 77.6309),
                0
        );

        assertThat(estimate.etaMinutes()).isEqualTo(Double.MAX_VALUE);
    }
}
