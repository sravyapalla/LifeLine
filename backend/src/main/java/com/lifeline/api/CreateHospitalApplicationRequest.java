package com.lifeline.api;

import com.lifeline.domain.EmergencyCondition;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record CreateHospitalApplicationRequest(
        @NotBlank String hospitalName,
        @NotBlank String contactName,
        @NotBlank String contactPhone,
        @NotBlank String addressText,
        @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") double longitude,
        @NotEmpty Set<EmergencyCondition> specialties,
        @Min(1) int totalBeds
) {
}
