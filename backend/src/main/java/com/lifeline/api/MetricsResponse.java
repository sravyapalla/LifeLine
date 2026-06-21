package com.lifeline.api;

public record MetricsResponse(
        int openIncidents,
        int availableAmbulances,
        int activeTrips,
        int hospitalsWithCapacity,
        double averageBedAvailabilityPercent,
        int pendingOutboxEvents,
        int publishedOutboxEvents,
        int failedOutboxEvents
) {
}
