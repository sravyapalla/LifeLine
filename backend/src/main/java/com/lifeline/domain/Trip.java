package com.lifeline.domain;

import java.time.Instant;

public record Trip(
        String id,
        String incidentId,
        String ambulanceId,
        String hospitalId,
        double pickupEtaMinutes,
        double hospitalEtaMinutes,
        double totalCost,
        Instant createdAt,
        TripStatus status
) {
    public Trip withStatus(TripStatus nextStatus) {
        return new Trip(id, incidentId, ambulanceId, hospitalId, pickupEtaMinutes, hospitalEtaMinutes, totalCost, createdAt, nextStatus);
    }

    public Trip withHospital(String nextHospitalId, double nextHospitalEtaMinutes, double nextTotalCost) {
        return new Trip(id, incidentId, ambulanceId, nextHospitalId, pickupEtaMinutes, nextHospitalEtaMinutes, nextTotalCost, createdAt, status);
    }
}
