package com.lifeline.notifications;

import com.lifeline.domain.Notification;
import com.lifeline.domain.NotificationRole;
import com.lifeline.domain.OutboxEvent;
import com.lifeline.store.LifeLineStore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final LifeLineStore store;

    public NotificationService(LifeLineStore store) {
        this.store = store;
    }

    public List<Notification> notificationsFor(NotificationRole role) {
        return store.notifications(role);
    }

    public Notification acknowledge(String notificationId) {
        return store.acknowledgeNotification(notificationId);
    }

    public void createForPublishedEvent(OutboxEvent event, Instant createdAt) {
        for (NotificationTemplate template : templatesFor(event)) {
            store.addNotification(new Notification(
                    "NOT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(),
                    template.role(),
                    template.title(),
                    template.message(),
                    event.id(),
                    event.eventType(),
                    createdAt,
                    null
            ));
        }
    }

    private List<NotificationTemplate> templatesFor(OutboxEvent event) {
        return switch (event.eventType()) {
            case "incident.created" -> List.of(
                    template(NotificationRole.PATIENT, "Request received", "Your emergency request is now in the dispatch queue."),
                    template(NotificationRole.CONTROL, "New incident queued", "Incident " + event.aggregateId() + " is waiting for dispatch.")
            );
            case "dispatch.reserved" -> List.of(
                    template(NotificationRole.PATIENT, "Ambulance assigned", "An ambulance and receiving hospital were reserved for your request."),
                    template(NotificationRole.DRIVER, "New trip assigned", "Trip " + event.aggregateId() + " is ready for pickup."),
                    template(NotificationRole.HOSPITAL, "Incoming patient", "A new trip is headed toward your facility."),
                    template(NotificationRole.CONTROL, "Dispatch reserved", "Trip " + event.aggregateId() + " was reserved and published.")
            );
            case "trip.status_changed" -> List.of(
                    template(NotificationRole.PATIENT, "Trip status updated", "Your care journey has a new ambulance status."),
                    template(NotificationRole.DRIVER, "Status recorded", "The latest trip status was published."),
                    template(NotificationRole.HOSPITAL, "Incoming status updated", "An incoming trip has changed status."),
                    template(NotificationRole.CONTROL, "Trip status published", "Trip " + event.aggregateId() + " status changed.")
            );
            case "hospital.capacity_changed" -> List.of(
                    template(NotificationRole.HOSPITAL, "Capacity updated", "Your current bed availability was published."),
                    template(NotificationRole.CONTROL, "Hospital capacity changed", "Hospital " + event.aggregateId() + " changed capacity.")
            );
            case "trip.rerouted" -> List.of(
                    template(NotificationRole.PATIENT, "Hospital route updated", "Your ambulance route was updated to another receiving hospital."),
                    template(NotificationRole.DRIVER, "Reroute assigned", "Trip " + event.aggregateId() + " has a new receiving hospital."),
                    template(NotificationRole.HOSPITAL, "Reroute published", "An incoming trip was rerouted."),
                    template(NotificationRole.CONTROL, "Trip rerouted", "Trip " + event.aggregateId() + " reroute was published.")
            );
            default -> List.of(
                    template(NotificationRole.CONTROL, "Workflow event published", event.eventType() + " was delivered.")
            );
        };
    }

    private NotificationTemplate template(NotificationRole role, String title, String message) {
        return new NotificationTemplate(role, title, message);
    }

    private record NotificationTemplate(NotificationRole role, String title, String message) {
    }
}
