package com.lifeline.api;

import com.lifeline.security.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginRequest(
        @NotBlank String username,
        @NotNull UserRole role,
        @NotBlank String password
) {
}
