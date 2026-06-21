package com.lifeline.security;

public record DemoUser(
        String username,
        String displayName,
        String passwordHash,
        UserRole role,
        String ambulanceId,
        String hospitalId
) {
    public AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(username, displayName, role, ambulanceId, hospitalId);
    }
}
