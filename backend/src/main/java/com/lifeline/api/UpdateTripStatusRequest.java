package com.lifeline.api;

import com.lifeline.domain.TripStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTripStatusRequest(
        @NotNull
        TripStatus status
) {
}
