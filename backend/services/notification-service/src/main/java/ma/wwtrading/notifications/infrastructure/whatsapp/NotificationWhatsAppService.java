package ma.wwtrading.notifications.infrastructure.whatsapp;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import ma.wwtrading.notifications.domain.NotificationEntity;

/**
 * WhatsApp notification provider abstraction.
 * By default (`notification.whatsapp.enabled=false`), WhatsApp dispatch is disabled
 * unless real Meta WhatsApp Cloud API / BSP credentials are explicitly configured.
 * It never invents credentials or claims a message was sent when unconfigured.
 */
@Service
public class NotificationWhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(NotificationWhatsAppService.class);

    private final boolean enabled;
    private final String apiBaseUrl;
    private final String phoneNumberId;
    private final String accessToken;
    private final String defaultCountryCode;
    private final RestClient restClient;

    @Autowired
    public NotificationWhatsAppService(
            @Value("${notification.whatsapp.enabled:false}") boolean enabled,
            @Value("${notification.whatsapp.api-base-url:https://graph.facebook.com/v20.0}") String apiBaseUrl,
            @Value("${notification.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${notification.whatsapp.access-token:}") String accessToken,
            @Value("${notification.whatsapp.default-country-code:+212}") String defaultCountryCode,
            RestClient.Builder restClientBuilder) {
        this.enabled = enabled;
        this.apiBaseUrl = apiBaseUrl != null ? apiBaseUrl.replaceAll("/+$", "") : "";
        this.phoneNumberId = phoneNumberId != null ? phoneNumberId.trim() : "";
        this.accessToken = accessToken != null ? accessToken.trim() : "";
        this.defaultCountryCode = defaultCountryCode != null && !defaultCountryCode.isBlank()
                ? defaultCountryCode.trim()
                : "+212";
        this.restClient = restClientBuilder != null ? restClientBuilder.build() : RestClient.create();
    }

    public NotificationWhatsAppService(
            boolean enabled, String apiBaseUrl, String phoneNumberId, String accessToken) {
        this(enabled, apiBaseUrl, phoneNumberId, accessToken, "+212", RestClient.builder());
    }

    public boolean isEnabledAndConfigured() {
        return enabled
                && !apiBaseUrl.isBlank()
                && !phoneNumberId.isBlank()
                && !accessToken.isBlank();
    }

    public String unconfiguredReason() {
        if (!enabled) {
            return "Fournisseur WhatsApp désactivé (notification.whatsapp.enabled=false)";
        }
        return "Identifiants du fournisseur WhatsApp non configurés (phone-number-id / access-token manquants)";
    }

    public String normalizePhoneNumber(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            throw new WhatsAppNotificationException("Numéro de téléphone destinataire manquant");
        }
        String cleaned = rawPhone.trim().replaceAll("[\\s\\-().]", "");
        if (cleaned.startsWith("00")) {
            cleaned = "+" + cleaned.substring(2);
        } else if (cleaned.startsWith("0") && cleaned.length() == 10) {
            cleaned = defaultCountryCode + cleaned.substring(1);
        } else if (!cleaned.startsWith("+") && cleaned.matches("^[1-9][0-9]{7,14}$")) {
            cleaned = "+" + cleaned;
        }
        if (!cleaned.matches("^\\+[1-9][0-9]{7,14}$")) {
            throw new WhatsAppNotificationException("Format de numéro de téléphone invalide pour WhatsApp");
        }
        return cleaned;
    }

    public String sendWhatsAppNotification(NotificationEntity notification) {
        if (notification == null) {
            throw new WhatsAppNotificationException("Notification is null");
        }
        String normalizedPhone = normalizePhoneNumber(notification.getRecipientPhone());
        if (!isEnabledAndConfigured()) {
            throw new WhatsAppNotificationException(unconfiguredReason());
        }

        String textBody = buildWhatsAppMessageText(notification);
        String endpoint = apiBaseUrl + "/" + phoneNumberId + "/messages";

        try {
            Map<String, Object> payload = Map.of(
                    "messaging_product", "whatsapp",
                    "recipient_type", "individual",
                    "to", normalizedPhone.substring(1),
                    "type", "text",
                    "text", Map.of(
                            "preview_url", false,
                            "body", textBody));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(endpoint)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            String providerMessageId = extractProviderMessageId(response);
            log.info("Sent WhatsApp notification ID {} to {} (providerMessageId={})",
                    notification.getId(), maskPhone(normalizedPhone), providerMessageId);
            return providerMessageId;
        } catch (Exception e) {
            throw new WhatsAppNotificationException("WhatsApp provider delivery failed: " + e.getMessage(), e);
        }
    }

    String buildWhatsAppMessageText(NotificationEntity notification) {
        StringBuilder sb = new StringBuilder();
        sb.append("*WWTRADING — ").append(notification.getTitle()).append("*\n\n");
        sb.append(notification.getMessage());
        if (notification.getActionUrl() != null && !notification.getActionUrl().isBlank()) {
            sb.append("\n\nSuivi : ").append(notification.getActionUrl());
        }
        return sb.toString();
    }

    private String extractProviderMessageId(Map<String, Object> response) {
        if (response == null) {
            return "wa-accepted";
        }
        Object messages = response.get("messages");
        if (messages instanceof java.util.List<?> list && !list.isEmpty()) {
            Object first = list.get(0);
            if (first instanceof Map<?, ?> map && map.get("id") != null) {
                return String.valueOf(map.get("id"));
            }
        }
        return "wa-accepted";
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 6) {
            return "***";
        }
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 2);
    }
}

