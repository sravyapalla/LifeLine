package com.lifeline.api;

import com.lifeline.domain.EmergencyCondition;
import com.lifeline.domain.IncidentPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateIncidentRequest(
        @NotBlank String patientName,
        @NotBlank String phone,
        @NotNull EmergencyCondition condition,
        @NotNull IncidentPriority priority,
        double latitude,
        double longitude
) {
}

