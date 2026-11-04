package ma.wwtrading.notifications.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification")
public class NotificationEntity {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "recipient_subject", nullable = false, length = 128)
    private String recipientSubject;

    @Column(name = "recipient_email", length = 320)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 32)
    private String recipientPhone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private NotificationChannel channel;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "action_url", length = 1000)
    private String actionUrl;

    @Column(name = "entity_type", length = 100)
    private String entityType;

    @Column(name = "entity_id", length = 255)
    private String entityId;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "source_event_id")
    private UUID sourceEventId;

    protected NotificationEntity() {
    }

    public NotificationEntity(
            String recipientSubject, String recipientEmail, NotificationType type,
            NotificationChannel channel, String title, String message, String actionUrl,
            String entityType, String entityId, Instant expiresAt, String metadata) {
        this(recipientSubject, recipientEmail, type, channel, title, message, actionUrl,
            entityType, entityId, expiresAt, metadata, null);
    }

    public NotificationEntity(
            String recipientSubject,
            String recipientEmail,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String message,
            String actionUrl,
            String entityType,
            String entityId,
            Instant expiresAt,
            String metadata,
            UUID sourceEventId) {
        this(recipientSubject, recipientEmail, null, type, channel, title, message, actionUrl,
                entityType, entityId, expiresAt, metadata, sourceEventId);
    }

    public NotificationEntity(
            String recipientSubject,
            String recipientEmail,
            String recipientPhone,
            NotificationType type,
            NotificationChannel channel,
            String title,
            String message,
            String actionUrl,
            String entityType,
            String entityId,
            Instant expiresAt,
            String metadata,
            UUID sourceEventId) {
        this.recipientSubject = required(recipientSubject, "Recipient subject");
        this.recipientEmail = optional(recipientEmail);
        this.recipientPhone = optional(recipientPhone);
        this.type = required(type, "Notification type");
        this.channel = required(channel, "Notification channel");
        this.title = required(title, "Notification title");
        this.message = required(message, "Notification message");
        this.actionUrl = optional(actionUrl);
        this.entityType = optional(entityType);
        this.entityId = optional(entityId);
        this.expiresAt = expiresAt;
        this.metadata = optional(metadata);
        this.sourceEventId = sourceEventId;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void markRead(Instant when) {
        if (readAt == null) {
            readAt = required(when, "Read timestamp");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static <T> T required(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getRecipientSubject() { return recipientSubject; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getRecipientPhone() { return recipientPhone; }
    public NotificationType getType() { return type; }
    public NotificationChannel getChannel() { return channel; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getActionUrl() { return actionUrl; }
    public String getEntityType() { return entityType; }
    public String getEntityId() { return entityId; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public String getMetadata() { return metadata; }
    public UUID getSourceEventId() { return sourceEventId; }
}
