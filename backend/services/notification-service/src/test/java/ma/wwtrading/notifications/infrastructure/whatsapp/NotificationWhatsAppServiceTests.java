package ma.wwtrading.notifications.infrastructure.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationEntity;
import ma.wwtrading.notifications.domain.NotificationType;
import org.junit.jupiter.api.Test;

class NotificationWhatsAppServiceTests {

    @Test
    void normalizesMoroccanAndInternationalPhoneNumbers() {
        NotificationWhatsAppService service = new NotificationWhatsAppService(false, "https://graph.facebook.com/v20.0", "", "");

        assertThat(service.normalizePhoneNumber("06 61 23 45 67")).isEqualTo("+212661234567");
        assertThat(service.normalizePhoneNumber("07-00-11-22-33")).isEqualTo("+212700112233");
        assertThat(service.normalizePhoneNumber("+212661234567")).isEqualTo("+212661234567");
        assertThat(service.normalizePhoneNumber("00212661234567")).isEqualTo("+212661234567");
        assertThat(service.normalizePhoneNumber("+33612345678")).isEqualTo("+33612345678");
    }

    @Test
    void rejectsBlankOrMalformedPhoneNumbers() {
        NotificationWhatsAppService service = new NotificationWhatsAppService(false, "https://graph.facebook.com/v20.0", "", "");

        assertThatThrownBy(() -> service.normalizePhoneNumber(" "))
            .isInstanceOf(WhatsAppNotificationException.class);
        assertThatThrownBy(() -> service.normalizePhoneNumber("abc-invalid"))
            .isInstanceOf(WhatsAppNotificationException.class);
    }

    @Test
    void reportsUnconfiguredReasonAndNeverClaimsSentWhenDisabled() {
        NotificationWhatsAppService disabledService = new NotificationWhatsAppService(false, "https://graph.facebook.com/v20.0", "", "");
        assertThat(disabledService.isEnabledAndConfigured()).isFalse();
        assertThat(disabledService.unconfiguredReason()).contains("notification.whatsapp.enabled=false");

        NotificationEntity entity = new NotificationEntity(
            "guest:123",
            "guest@example.ma",
            "+212661234567",
            NotificationType.ORDER_CREATED,
            NotificationChannel.ALL,
            "Confirmation de commande WWT-001",
            "Votre commande est enregistrée.",
            "http://localhost:4201/suivi-commande?token=xyz",
            "ORDER",
            "123",
            null,
            null,
            null
        );

        assertThatThrownBy(() -> disabledService.sendWhatsAppNotification(entity))
            .isInstanceOf(WhatsAppNotificationException.class)
            .hasMessageContaining("notification.whatsapp.enabled=false");
    }
}

