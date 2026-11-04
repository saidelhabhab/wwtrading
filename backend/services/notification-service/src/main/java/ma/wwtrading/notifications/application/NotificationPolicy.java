package ma.wwtrading.notifications.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import ma.wwtrading.events.EventPayloads;
import ma.wwtrading.notifications.domain.NotificationChannel;
import ma.wwtrading.notifications.domain.NotificationType;
import ma.wwtrading.notifications.infrastructure.client.UserServiceClient.ClientRecipient;

/** Central, trusted mapping from domain events to user-visible notification commands. */
@Component
public class NotificationPolicy {

    private static final DateTimeFormatter FRENCH_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'à' HH:mm", Locale.FRENCH)
                    .withZone(ZoneId.of("Africa/Casablanca"));

    public CreateNotificationCommand quotationCreated(UUID eventId, EventPayloads.QuotationCreated payload,
            ClientRecipient recipient) {
        return command(recipient.keycloakSubject(), recipient.email(), NotificationType.QUOTATION_CREATED,
                NotificationChannel.BOTH, "Demande de devis reçue",
                "Votre demande de devis a bien été enregistrée par WWTRADING.",
                "/devis/mes-demandes/" + payload.quotationId(), "QUOTATION", payload.quotationId(), eventId);
    }

    public CreateNotificationCommand quotationStatusChanged(UUID eventId,
            EventPayloads.QuotationStatusChanged payload, ClientRecipient recipient) {
        return command(recipient.keycloakSubject(), recipient.email(), NotificationType.QUOTATION_STATUS_CHANGED,
                NotificationChannel.BOTH, "Statut du devis mis à jour",
                "Le statut de votre devis est maintenant : " + label(payload.status()) + ".",
                "/devis/mes-demandes/" + payload.quotationId(), "QUOTATION", payload.quotationId(), eventId);
    }

    public CreateNotificationCommand maintenanceClient(UUID eventId, UUID id, String subject, String email,
            NotificationType type, String title, String message) {
        return command(subject, email, type, NotificationChannel.BOTH, title, message,
                "/client/maintenance/" + id, "MAINTENANCE", id, eventId);
    }

    public CreateNotificationCommand maintenanceTechnician(UUID eventId, UUID id, String subject) {
        return command(subject, null, NotificationType.MAINTENANCE_ASSIGNED, NotificationChannel.IN_APP,
                "Nouvelle intervention assignée", "Une demande de maintenance vous a été assignée.",
                "/technician/maintenance/" + id, "MAINTENANCE", id, eventId);
    }

    public CreateNotificationCommand supportMessage(UUID eventId, UUID conversationId, String recipientSubject) {
        return command(recipientSubject, null, NotificationType.SUPPORT_MESSAGE, NotificationChannel.IN_APP,
                "Nouveau message support", "Un client a envoyé un nouveau message au support.",
                supportUrl("COMMERCIAL", conversationId), "SUPPORT_CONVERSATION", conversationId, eventId);
    }

    /**
     * Role-aware support notification: the action URL targets the support route the
     * recipient is authorized to use (Angular exposes /admin/support, /manager/support
     * and /commercial/support, all backed by the same support workspace).
     */
    public CreateNotificationCommand supportMessageForStaff(UUID eventId, UUID conversationId, String recipientSubject,
            String recipientRole) {
        return command(recipientSubject, null, NotificationType.SUPPORT_MESSAGE, NotificationChannel.IN_APP,
                "Nouveau message support", "Un client a envoyé un nouveau message au support.",
                supportUrl(recipientRole, conversationId), "SUPPORT_CONVERSATION", conversationId, eventId);
    }

    public static String supportUrl(String role, UUID conversationId) {
        String safeRole = role == null ? "COMMERCIAL" : role.trim().toUpperCase();
        if (!Set.of("ADMIN", "MANAGER", "COMMERCIAL").contains(safeRole)) {
            throw new IllegalArgumentException("Unauthorized support notification role: " + safeRole);
        }
        return "/" + safeRole.toLowerCase() + "/support?conversation=" + conversationId;
    }

    public CreateNotificationCommand internal(UUID eventId, NotificationType type, String subject, String email,
            String title, String message, String actionUrl, String entityType, UUID entityId) {
        return command(subject, email, type, NotificationChannel.IN_APP, title, message, actionUrl,
                entityType, entityId, eventId);
    }

    public CreateNotificationCommand client(UUID eventId, NotificationType type, ClientRecipient recipient,
            String title, String message, String actionUrl, String entityType, UUID entityId) {
        return command(recipient.keycloakSubject(), recipient.email(), type, NotificationChannel.BOTH,
                title, message, actionUrl, entityType, entityId, eventId);
    }

    public CreateNotificationCommand orderCreatedForCustomer(
            UUID eventId,
            EventPayloads.OrderCreated payload,
            String recipientSubject,
            String recipientEmail,
            String recipientPhone) {
        String orderRef = resolveOrderRef(payload.orderNumber(), payload.orderId());
        String actionUrl = resolveActionUrl(payload.orderId(), orderRef, payload.trackingUrl(), payload.guestOrder(),
                payload.customerSubject(), recipientSubject);
        boolean hasPhone = recipientPhone != null && !recipientPhone.isBlank();
        NotificationChannel channel = hasPhone ? NotificationChannel.ALL : NotificationChannel.BOTH;
        String title = "Confirmation de commande " + orderRef;
        String message = buildOrderCreatedMessage(payload, orderRef);
        return new CreateNotificationCommand(
                recipientSubject,
                recipientEmail,
                recipientPhone,
                NotificationType.ORDER_CREATED,
                channel,
                title,
                message,
                actionUrl,
                "ORDER",
                payload.orderId().toString(),
                null,
                null,
                eventId);
    }

    public CreateNotificationCommand orderStatusChangedForCustomer(
            UUID eventId,
            EventPayloads.OrderStatusChanged payload,
            String recipientSubject,
            String recipientEmail,
            String recipientPhone,
            String humanStatus) {
        String orderRef = resolveOrderRef(payload.orderNumber(), payload.orderId());
        String actionUrl = resolveActionUrl(payload.orderId(), orderRef, payload.trackingUrl(), payload.guestOrder(),
                payload.customerSubject(), recipientSubject);
        boolean hasPhone = recipientPhone != null && !recipientPhone.isBlank();
        NotificationChannel channel = hasPhone ? NotificationChannel.ALL : NotificationChannel.BOTH;
        String title = "Commande " + orderRef + " — " + humanStatus;
        String message = "Bonjour"
                + (payload.customerName() != null && !payload.customerName().isBlank() ? " " + payload.customerName() : "")
                + ",\n\nLe statut de votre commande " + orderRef + " a été mis à jour : " + humanStatus + "."
                + "\n\nService client WWTRADING : contact@wwtrading.ma · Kénitra, Maroc";
        return new CreateNotificationCommand(
                recipientSubject,
                recipientEmail,
                recipientPhone,
                NotificationType.ORDER_STATUS_CHANGED,
                channel,
                title,
                message,
                actionUrl,
                "ORDER",
                payload.orderId().toString(),
                null,
                null,
                eventId);
    }

    public CreateNotificationCommand paymentStatusChangedForCustomer(
            UUID eventId,
            EventPayloads.PaymentStatusChanged payload,
            String recipientSubject,
            String recipientEmail,
            String recipientPhone,
            String humanPaymentStatus) {
        String orderRef = resolveOrderRef(payload.orderNumber(), payload.orderId());
        String actionUrl = resolveActionUrl(payload.orderId(), orderRef, payload.trackingUrl(), payload.guestOrder(),
                payload.customerSubject(), recipientSubject);
        boolean hasPhone = recipientPhone != null && !recipientPhone.isBlank();
        NotificationChannel channel = hasPhone ? NotificationChannel.ALL : NotificationChannel.BOTH;
        String title = "Paiement commande " + orderRef + " — " + humanPaymentStatus;
        String message = "Bonjour"
                + (payload.customerName() != null && !payload.customerName().isBlank() ? " " + payload.customerName() : "")
                + ",\n\nLe statut de paiement de votre commande " + orderRef + " a été mis à jour : "
                + humanPaymentStatus + "."
                + "\n\nService client WWTRADING : contact@wwtrading.ma · Kénitra, Maroc";
        return new CreateNotificationCommand(
                recipientSubject,
                recipientEmail,
                recipientPhone,
                NotificationType.PAYMENT_STATUS_CHANGED,
                channel,
                title,
                message,
                actionUrl,
                "ORDER",
                payload.orderId().toString(),
                null,
                null,
                eventId);
    }

    public CreateNotificationCommand supportReply(UUID eventId, UUID conversationId, ClientRecipient recipient) {
        return command(recipient.keycloakSubject(), recipient.email(), NotificationType.SUPPORT_REPLY,
                NotificationChannel.BOTH, "Réponse du support",
                "L’équipe WWTRADING a répondu à votre conversation support.",
                "/client/support?conversation=" + conversationId, "SUPPORT_CONVERSATION", conversationId, eventId);
    }

    private String buildOrderCreatedMessage(EventPayloads.OrderCreated payload, String orderRef) {
        if (payload.items() == null || payload.items().isEmpty()) {
            return "Votre commande " + orderRef + " a bien été enregistrée par WWTRADING.";
        }
        StringBuilder sb = new StringBuilder();
        String customerName = payload.customerName() != null && !payload.customerName().isBlank()
                ? payload.customerName().trim()
                : "Client";
        sb.append("Bonjour ").append(customerName).append(",\n\n");
        sb.append("Votre commande a bien été enregistrée par WWTRADING.\n\n");
        sb.append("Référence de commande : ").append(orderRef).append("\n");
        if (payload.createdAt() != null) {
            sb.append("Date de commande : ").append(FRENCH_DATE_FORMAT.format(payload.createdAt())).append("\n");
        }

        sb.append("\nArticles commandés :\n");
        for (EventPayloads.OrderLineItem item : payload.items()) {
            String label = item.productName() != null && !item.productName().isBlank()
                    ? item.productName() + " (" + item.productReference() + ")"
                    : item.productReference();
            sb.append("- ").append(label)
                    .append(" × ").append(item.quantity())
                    .append(" — ").append(formatMad(item.unitPrice())).append(" / unité")
                    .append(" = ").append(formatMad(item.lineTotal())).append("\n");
        }

        sb.append("\nRécapitulatif financier :\n");
        if (payload.subtotal() != null) {
            sb.append("- Sous-total articles : ").append(formatMad(payload.subtotal())).append("\n");
        }
        if (payload.discount() != null && payload.discount().signum() > 0) {
            sb.append("- Remise commerciale : -").append(formatMad(payload.discount())).append("\n");
        }
        if (payload.deliveryFee() != null) {
            sb.append("- Frais de livraison : ").append(
                    payload.deliveryFee().signum() == 0 ? "Gratuite (0,00 MAD)" : formatMad(payload.deliveryFee())
            ).append("\n");
        }
        sb.append("- Total TTC : ").append(formatMad(payload.total())).append("\n");

        sb.append("\nMode de réception :\n");
        if ("PICKUP".equalsIgnoreCase(payload.deliveryMethod())) {
            sb.append("- Retrait au showroom WWTRADING (Kénitra, Maroc)\n");
        } else {
            sb.append("- Livraison à domicile");
            if (payload.deliveryAddressSummary() != null && !payload.deliveryAddressSummary().isBlank()) {
                sb.append(" : ").append(payload.deliveryAddressSummary());
            }
            sb.append("\n");
        }

        sb.append("\nMode de paiement et instructions de virement :\n");
        if ("SPLIT_50_50".equalsIgnoreCase(payload.paymentMethod())
                || "SPLIT_50_50".equalsIgnoreCase(payload.paymentPlan())) {
            sb.append("- Option choisie : 50 % à la commande (virement) + 50 % à la livraison\n");
            sb.append("- Acompte de 50 % à régler maintenant par virement : ")
                    .append(formatMad(payload.upfrontPaymentAmount())).append("\n");
            sb.append("- Solde de 50 % à régler à la livraison : ")
                    .append(formatMad(payload.remainingDeliveryAmount())).append("\n");
        } else {
            sb.append("- Option choisie : Virement bancaire intégral (100 %)\n");
            sb.append("- Montant à régler par virement : ").append(formatMad(payload.total())).append("\n");
        }
        if (payload.paymentBankName() != null && !payload.paymentBankName().isBlank()) {
            sb.append("- Banque : ").append(payload.paymentBankName()).append("\n");
        }
        if (payload.paymentBankBeneficiary() != null && !payload.paymentBankBeneficiary().isBlank()) {
            sb.append("- Bénéficiaire : ").append(payload.paymentBankBeneficiary()).append("\n");
        }
        if (payload.paymentBankRib() != null && !payload.paymentBankRib().isBlank()) {
            sb.append("- RIB : ").append(payload.paymentBankRib()).append("\n");
        }
        sb.append("- Motif du virement : ").append(orderRef).append("\n");

        if (payload.trackingUrl() != null && !payload.trackingUrl().isBlank()) {
            sb.append("\nLien de suivi sécurisé : ").append(payload.trackingUrl()).append("\n");
        }

        sb.append("\nService client WWTRADING : contact@wwtrading.ma · Kénitra, Maroc");
        return sb.toString();
    }

    private static String resolveActionUrl(UUID orderId, String orderRef, String trackingUrl,
            boolean guestOrder, String customerSubject, String recipientSubject) {
        boolean isLinkedToClient = (customerSubject != null && !customerSubject.isBlank() && !customerSubject.startsWith("guest:"))
                || (recipientSubject != null && !recipientSubject.isBlank() && !recipientSubject.startsWith("guest:"));
        if (isLinkedToClient) {
            return "/client/orders/" + orderId;
        }
        if (trackingUrl != null && !trackingUrl.isBlank()) {
            return trackingUrl;
        }
        return "/suivi-commande?orderNumber=" + orderRef;
    }

    private static String resolveOrderRef(String orderNumber, UUID orderId) {
        if (orderNumber != null && !orderNumber.isBlank()) {
            return orderNumber.trim();
        }
        if (orderId != null) {
            return "WWT-" + orderId.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        }
        return "WWT";
    }

    private static String formatMad(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount.setScale(2, RoundingMode.HALF_UP);
        return safe.toPlainString().replace('.', ',') + " MAD";
    }

    private CreateNotificationCommand command(String subject, String email, NotificationType type,
            NotificationChannel channel, String title, String message, String actionUrl,
            String entityType, UUID entityId, UUID eventId) {
        return new CreateNotificationCommand(subject, email, null, type, channel, title, message,
                actionUrl, entityType, entityId.toString(), null, null, eventId);
    }

    private String label(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "En attente";
            case "IN_REVIEW" -> "En cours d’étude";
            case "QUOTED" -> "Devis proposé";
            case "ACCEPTED" -> "Accepté";
            case "REJECTED" -> "Refusé";
            case "CANCELLED" -> "Annulé";
            case "SUBMITTED" -> "Soumise";
            case "ASSIGNED" -> "Assignée";
            case "IN_PROGRESS" -> "En cours";
            case "WAITING_FOR_PART" -> "En attente de pièce";
            case "COMPLETION_PENDING_CONFIRMATION" -> "Confirmation client requise";
            case "COMPLETED" -> "Terminée";
            default -> status;
        };
    }
}
