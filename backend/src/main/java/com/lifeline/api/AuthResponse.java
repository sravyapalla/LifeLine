package com.lifeline.api;

import com.lifeline.security.AuthenticatedUser;

import java.time.Instant;

public record AuthResponse(
        String token,
        Instant expiresAt,
        AuthenticatedUser user
) {
}
