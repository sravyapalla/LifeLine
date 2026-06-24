package com.lifeline.api;

import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.IncidentPriority;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIncidentRequest(
        @NotBlank String patientName,
        @NotBlank String phone,
        @NotNull EmergencyCondition condition,
        @NotNull IncidentPriority priority,
        @NotBlank String addressText,
        String landmark,
        String locationSource,
        @DecimalMin("-90.0") @DecimalMax("90.0")
        double latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0")
        double longitude
) {
}
