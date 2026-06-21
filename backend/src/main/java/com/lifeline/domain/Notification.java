package com.lifeline.domain;

import java.time.Instant;

public record Notification(
        String id,
        NotificationRole role,
        String title,
        String message,
        String eventId,
        String eventType,
        Instant createdAt,
        Instant acknowledgedAt
) {
    public boolean unread() {
        return acknowledgedAt == null;
    }

    public Notification acknowledged(Instant acknowledgedAt) {
        return new Notification(id, role, title, message, eventId, eventType, createdAt, acknowledgedAt);
    }
}
