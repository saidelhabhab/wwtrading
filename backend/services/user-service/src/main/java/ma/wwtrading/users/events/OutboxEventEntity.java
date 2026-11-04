package ma.wwtrading.users.events;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "event_outbox")
class OutboxEventEntity {

    @Id
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(nullable = false, length = 160)
    private String topic;

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private int attempts;

    protected OutboxEventEntity() {
    }

    static OutboxEventEntity pending(
        UUID id,
        UUID aggregateId,
        String topic,
        String eventKey,
        String payload,
        Instant occurredAt
    ) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.id = id;
        event.aggregateId = aggregateId;
        event.topic = topic;
        event.eventKey = eventKey;
        event.payload = payload;
        event.occurredAt = occurredAt;
        return event;
    }

    void markPublished() {
        attempts++;
        publishedAt = Instant.now();
    }

    void markAttemptFailed() {
        attempts++;
    }

    UUID getId() { return id; }
    UUID getAggregateId() { return aggregateId; }
    String getTopic() { return topic; }
    String getEventKey() { return eventKey; }
    String getPayload() { return payload; }
    Instant getOccurredAt() { return occurredAt; }
    Instant getPublishedAt() { return publishedAt; }
    int getAttempts() { return attempts; }
}
