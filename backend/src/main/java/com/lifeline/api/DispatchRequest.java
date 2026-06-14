package com.lifeline.api;

import jakarta.validation.constraints.NotBlank;

public record DispatchRequest(@NotBlank String incidentId) {
}

