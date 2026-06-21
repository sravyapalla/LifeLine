package com.lifeline.api;

import jakarta.validation.constraints.Min;

public record UpdateHospitalCapacityRequest(
        @Min(0)
        int availableBeds
) {
}
