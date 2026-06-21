package com.lifeline.routing;

import com.lifeline.domain.Location;

public interface RoutingProvider {
    RouteEstimate estimate(Location from, Location to, double averageSpeedKmph);
}
