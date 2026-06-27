package com.lifeline.security;

public record AuthenticatedUser(
        String username,
        String displayName,
        UserRole role,
        String ambulanceId,
        String hospitalId,
        String status
) {
    public AuthenticatedUser(String username, String displayName, UserRole role, String ambulanceId, String hospitalId) {
        this(username, displayName, role, ambulanceId, hospitalId, "APPROVED");
    }

    public boolean isControl() {
        return role == UserRole.CONTROL;
    }

    public boolean approved() {
        return "APPROVED".equals(status);
    }
}
