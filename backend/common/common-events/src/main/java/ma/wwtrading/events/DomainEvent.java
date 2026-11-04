package ma.wwtrading.events;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Transport-neutral envelope shared by WWTRADING domain events.
 *
 * <p>{@link Instant} is an absolute UTC timeline value. The optional actor is
 * always a Keycloak subject and must never contain a token or user credential.</p>
 */
public record DomainEvent<T>(
    UUID eventId,
    String eventType,
    String eventVersion,
    Instant occurredAt,
    String correlationId,
    String producer,
    String aggregateType,
    String aggregateId,
    String actorSubject,
    T payload
) {
    public DomainEvent {
        eventId = Objects.requireNonNull(eventId, "Event ID is required");
        eventType = required(eventType, "Event type");
        eventVersion = required(eventVersion, "Event version");
        occurredAt = Objects.requireNonNull(occurredAt, "Event occurrence time is required");
        correlationId = optional(correlationId);
        producer = required(producer, "Event producer");
        aggregateType = required(aggregateType, "Aggregate type");
        aggregateId = required(aggregateId, "Aggregate ID");
        actorSubject = optional(actorSubject);
        payload = Objects.requireNonNull(payload, "Event payload is required");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
