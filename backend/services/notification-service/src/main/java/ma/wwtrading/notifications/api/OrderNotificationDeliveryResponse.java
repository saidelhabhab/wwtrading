package ma.wwtrading.notifications.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.notifications.domain.NotificationType;

public record OrderNotificationDeliveryResponse(
        UUID orderId,
        String overallEmailStatus,
        String overallWhatsAppStatus,
        String lastEmailError,
        String lastWhatsAppError,
        List<NotificationDeliveryItem> deliveries) {

    public record NotificationDeliveryItem(
            UUID notificationId,
            NotificationType type,
            String title,
            String recipientEmail,
            String recipientPhone,
            Instant createdAt,
            String emailStatus,
            int emailAttempts,
            Instant emailSentAt,
            String emailLastError,
            String whatsAppStatus,
            int whatsAppAttempts,
            String whatsAppProviderMessageId,
            Instant whatsAppSentAt,
            String whatsAppLastError) {
    }
}

