package ma.wwtrading.notifications.infrastructure.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.internet.MimeMessage;
import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationEntity;
import ma.wwtrading.notifications.domain.NotificationType;

@ExtendWith(MockitoExtension.class)
class NotificationEmailServiceTests {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private MimeMessage mimeMessage;

    private NotificationEmailService service;

    @BeforeEach
    void setUp() {
        service = new NotificationEmailService(
                mailSender,
                "no-reply@wwtrading.ma",
                "WWTRADING",
                "http://localhost:4201"
        );
    }

    @Test
    void buildsFullActionUrlWithRelativePath() {
        String fullUrl = service.buildFullActionUrl("/quotes/123");
        assertThat(fullUrl).isEqualTo("http://localhost:4201/quotes/123");
    }

    @Test
    void buildsFullActionUrlWithAbsoluteUrl() {
        String fullUrl = service.buildFullActionUrl("https://external.example.com/page");
        assertThat(fullUrl).isEqualTo("https://external.example.com/page");
    }

    @Test
    void buildsHtmlTemplateWithEscapedContentAndActionButton() {
        String html = service.buildHtmlTemplate(
                "Devis <#123>",
                "Message line 1\nMessage line 2 & test",
                "/quotes/123",
                false,
                null
        );

        assertThat(html).contains("Devis &lt;#123&gt;");
        assertThat(html).contains("Message line 1<br>Message line 2 &amp; test");
        assertThat(html).contains("http://localhost:4201/quotes/123");
        assertThat(html).contains("Consulter");
    }

    @Test
    void sendsNotificationEmailSuccessfully() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        NotificationEntity entity = new NotificationEntity(
                "user1", "client@example.com", NotificationType.QUOTATION_CREATED, NotificationChannel.EMAIL,
                "Nouveau Devis", "Un nouveau devis est disponible.", "/quotes/456", "QUOTATION", "456", null, null);

        service.sendNotificationEmail(entity);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void propagatesSmtpExceptionForPersistedRetryHandling() {
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("Mail server down")).when(mailSender).send(any(MimeMessage.class));

        NotificationEntity entity = new NotificationEntity(
                "user1", "client@example.com", NotificationType.QUOTATION_CREATED, NotificationChannel.EMAIL,
                "Title", "Message", null, null, null, null, null);

        assertThatThrownBy(() -> service.sendNotificationEmail(entity))
            .isInstanceOf(NotificationEmailException.class);

        verify(mailSender).send(mimeMessage);
    }

    @Test
    void missingEmailIsARecoverableDeliveryFailure() {
        NotificationEntity entity = new NotificationEntity(
                "user1", "", NotificationType.QUOTATION_CREATED, NotificationChannel.EMAIL,
                "Title", "Message", null, null, null, null, null);

        assertThatThrownBy(() -> service.sendNotificationEmail(entity))
            .isInstanceOf(NotificationEmailException.class);

        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void providesPlainTextAlternative() {
        NotificationEntity entity = new NotificationEntity(
                "user1", "client@example.com", NotificationType.SYSTEM, NotificationChannel.EMAIL,
                "Title", "Message", "/notifications", null, null, null, null);
        assertThat(service.buildPlainText(entity))
            .contains("Title", "Message", "http://localhost:4201/notifications", "WWTRADING");
    }

    @Test
    void buildsHtmlTemplateForGuestOrderTrackingUrl() {
        String trackingUrl = "http://localhost:4201/suivi-commande?token=secret12345678901234567890123456789012";
        String html = service.buildHtmlTemplate(
                "Confirmation de commande WWT-4AA9B0CE",
                "Bonjour hamza,\n\nVotre commande a bien été enregistrée.\n\nArticles commandés :\n- Konica Minolta bizhub 458 × 1 — 14000,00 MAD / unité = 14000,00 MAD\n\nRécapitulatif financier :\n- Sous-total articles : 14000,00 MAD\n- Frais de livraison : Gratuite\n- Total TTC : 14000,00 MAD",
                trackingUrl,
                true,
                "hamza"
        );

        assertThat(html).contains("Suivre ma commande en direct");
        assertThat(html).contains("secret12345678901234567890123456789012");
        assertThat(html).contains("Konica Minolta bizhub 458");
        assertThat(html).contains("Total TTC");
        assertThat(html).contains("WWTRADING SARL");
    }

    @Test
    void buildsHtmlTemplateForClientOrderUrl() {
        String clientUrl = "/client/orders/56127e80-e5a4-4117-bf8b-af329d8adfb0";
        String html = service.buildHtmlTemplate(
                "Commande WWT-56127E80 — Terminée",
                "Bonjour jamal karout,\n\nLe statut de votre commande WWT-56127E80 a été mis à jour : Terminée.",
                clientUrl,
                true,
                "jamal karout"
        );

        assertThat(html).contains("Accéder à mon espace commande");
        assertThat(html).contains("http://localhost:4201/client/orders/56127e80-e5a4-4117-bf8b-af329d8adfb0");
        assertThat(html).contains("Terminée");
    }
}
