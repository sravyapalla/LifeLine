package com.lifeline.security;

public record AuthenticatedUser(
        String username,
        String displayName,
        UserRole role,
        String ambulanceId,
        String hospitalId
) {
    public boolean isControl() {
        return role == UserRole.CONTROL;
    }
}
