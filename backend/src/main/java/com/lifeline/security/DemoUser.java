package com.lifeline.security;

public record DemoUser(
        String username,
        String displayName,
        String passwordHash,
        UserRole role,
        String ambulanceId,
        String hospitalId,
        String status
) {
    public DemoUser(String username, String displayName, String passwordHash, UserRole role, String ambulanceId, String hospitalId) {
        this(username, displayName, passwordHash, role, ambulanceId, hospitalId, "APPROVED");
    }

    public AuthenticatedUser authenticatedUser() {
        return new AuthenticatedUser(username, displayName, role, ambulanceId, hospitalId, status);
    }
}
