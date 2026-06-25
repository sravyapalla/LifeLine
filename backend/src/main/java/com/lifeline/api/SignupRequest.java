package com.lifeline.api;

import com.lifeline.security.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SignupRequest(
        @Email @NotBlank String email,
        @NotBlank String displayName,
        @NotBlank String password,
        @NotNull UserRole role
) {
}
