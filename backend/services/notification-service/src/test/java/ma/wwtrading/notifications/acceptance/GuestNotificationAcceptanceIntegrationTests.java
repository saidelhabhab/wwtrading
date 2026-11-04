package ma.wwtrading.notifications.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationEntity;
import ma.wwtrading.notifications.domain.NotificationType;
import ma.wwtrading.notifications.infrastructure.email.NotificationEmailService;
import ma.wwtrading.notifications.infrastructure.whatsapp.NotificationWhatsAppService;
import ma.wwtrading.notifications.infrastructure.whatsapp.WhatsAppNotificationException;

public class GuestNotificationAcceptanceIntegrationTests {

    @Test
    @DisplayName("Scenario 4: Live MailHog Email Verification for Guest Order")
    void sendsGuestEmailToMailHogAndVerifiesPresenceAndRenderedContent() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(
            isMailHogRunning(),
            "MailHog is not available on localhost:1025 - skipping live acceptance test"
        );

        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("localhost");
        mailSender.setPort(1025);

        NotificationEmailService emailService = new NotificationEmailService(
            mailSender,
            "no-reply@wwtrading.ma",
            "WWTRADING",
            "http://localhost:4201"
        );

        String testOrderNumber = "WWT-TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String testTrackingUrl = "http://localhost:4201/suivi-commande?token=acceptance-test-token-" + UUID.randomUUID();
        String testEmail = "guest.acceptance@" + UUID.randomUUID().toString().substring(0, 6) + ".ma";

        NotificationEntity notificationEntity = new NotificationEntity(
            "guest:acceptance-test",
            testEmail,
            "+212661001122",
            NotificationType.ORDER_CREATED,
            NotificationChannel.EMAIL,
            "Confirmation de commande " + testOrderNumber,
            "Votre commande " + testOrderNumber + " d'un montant de 15 300,00 DH a été enregistrée avec succès.\n"
                + "Mode de paiement : Virement bancaire intégral (100 %)\n"
                + "Banque : CIH Bank — RIB : MA640001111122223333444455\n"
                + "Bénéficiaire : WWTRADING SARL\n"
                + "Motif de virement : " + testOrderNumber,
            testTrackingUrl,
            "ORDER",
            UUID.randomUUID().toString(),
            null,
            null,
            null
        );

        // 1. Send real email through SMTP port 1025 to MailHog
        emailService.sendNotificationEmail(notificationEntity);

        // 2. Query MailHog REST API on port 8025 to verify actual delivery
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:8025/api/v2/messages"))
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String json = response.body();

        // 3. Confirm exact message content in MailHog
        assertThat(json).contains(testEmail);
        assertThat(json).contains(testOrderNumber);
        assertThat(json).contains("MA640001111122223333444455");
        String normalizedJson = json.replace("=\\r\\n", "").replace("=\r\n", "").replace("=3D", "=");
        assertThat(normalizedJson).contains(testTrackingUrl);
    }

    @Test
    @DisplayName("Scenario 4: Truthful WhatsApp Disabled Status & Safe Execution")
    void recordsTruthfulDisabledStatusWithoutBlockingOrderFlow() {
        NotificationWhatsAppService whatsAppService = new NotificationWhatsAppService(
            false,
            "https://graph.facebook.com/v20.0",
            "",
            ""
        );

        assertThat(whatsAppService.isEnabledAndConfigured()).isFalse();
        assertThat(whatsAppService.unconfiguredReason())
            .isEqualTo("Fournisseur WhatsApp désactivé (notification.whatsapp.enabled=false)");

        NotificationEntity entity = new NotificationEntity(
            "guest:test",
            "guest@example.ma",
            "+212661223344",
            NotificationType.ORDER_CREATED,
            NotificationChannel.ALL,
            "Commande WWT-100",
            "Détails commande",
            "http://localhost:4201/suivi-commande?token=abc",
            "ORDER",
            "100",
            null,
            null,
            null
        );

        // Never claims sent, raises explicit WhatsAppNotificationException with clear reason
        org.junit.jupiter.api.Assertions.assertThrows(
            WhatsAppNotificationException.class,
            () -> whatsAppService.sendWhatsAppNotification(entity)
        );
    }

    private boolean isMailHogRunning() {
        try (var socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 1025), 400);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
