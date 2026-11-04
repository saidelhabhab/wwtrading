package ma.wwtrading.notifications.application;

import java.time.Instant;
import java.util.UUID;

import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationType;

public record CreateNotificationCommand(
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

    public CreateNotificationCommand(
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

    public CreateNotificationCommand(
            String recipientSubject, String recipientEmail, NotificationType type,
            NotificationChannel channel, String title, String message, String actionUrl,
            String entityType, String entityId, Instant expiresAt, String metadata) {
        this(recipientSubject, recipientEmail, null, type, channel, title, message, actionUrl,
            entityType, entityId, expiresAt, metadata, null);
    }
}
