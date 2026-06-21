package com.lifeline.routing;

import com.lifeline.domain.Location;
import org.springframework.stereotype.Service;

@Service
public class StraightLineRoutingProvider implements RoutingProvider {
    @Override
    public RouteEstimate estimate(Location from, Location to, double averageSpeedKmph) {
        double distanceKm = distanceKm(from, to);
        if (averageSpeedKmph <= 0) {
            return new RouteEstimate(distanceKm, Double.MAX_VALUE);
        }
        return new RouteEstimate(distanceKm, distanceKm / averageSpeedKmph * 60);
    }

    private double distanceKm(Location from, Location to) {
        double earthRadiusKm = 6371;
        double dLat = Math.toRadians(to.latitude() - from.latitude());
        double dLng = Math.toRadians(to.longitude() - from.longitude());
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2)
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }
}
