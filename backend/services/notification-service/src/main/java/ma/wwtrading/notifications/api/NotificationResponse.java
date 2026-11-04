package ma.wwtrading.notifications.api;

import java.time.Instant;
import java.util.UUID;

import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationType;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        NotificationChannel channel,
        String title,
        String message,
        String actionUrl,
        String entityType,
        String entityId,
        Instant readAt,
        Instant createdAt) {
}
