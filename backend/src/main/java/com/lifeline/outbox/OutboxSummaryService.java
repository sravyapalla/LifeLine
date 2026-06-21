package com.lifeline.outbox;

import com.lifeline.domain.OutboxEvent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class OutboxSummaryService {
    public OutboxSummary summarize(List<OutboxEvent> events, Instant now) {
        int pending = (int) events.stream().filter(event -> event.publishedAt() == null).count();
        int published = events.size() - pending;

        OutboxEvent oldestPending = events.stream()
                .filter(event -> event.publishedAt() == null)
                .min(Comparator.comparing(OutboxEvent::createdAt))
                .orElse(null);

        Instant lastPublishedAt = events.stream()
                .map(OutboxEvent::publishedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);

        List<OutboxEventTypeSummary> eventTypes = events.stream()
                .collect(Collectors.groupingBy(
                        OutboxEvent::eventType,
                        LinkedHashMap::new,
                        Collectors.toList()
                ))
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> summarizeEventType(entry.getKey(), entry.getValue()))
                .toList();

        return new OutboxSummary(
                events.size(),
                pending,
                published,
                oldestPending == null ? 0 : Math.max(0, Duration.between(oldestPending.createdAt(), now).toSeconds()),
                oldestPending == null ? null : oldestPending.createdAt(),
                lastPublishedAt,
                eventTypes
        );
    }

    private OutboxEventTypeSummary summarizeEventType(String eventType, List<OutboxEvent> events) {
        int pending = (int) events.stream().filter(event -> event.publishedAt() == null).count();
        return new OutboxEventTypeSummary(
                eventType,
                events.size(),
                pending,
                events.size() - pending
        );
    }
}
