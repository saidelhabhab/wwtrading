package ma.wwtrading.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Minimal, transport-neutral payload contracts for version-one domain events. */
public final class EventPayloads {
    private EventPayloads() {
    }

    public record UserCreated(String subject, String email, String displayName) {
    }

    public record ProductCreated(
        UUID productId, String reference, String name, String brand, String model, String productType
    ) {
    }

    public record OrderLineItem(
        String productReference,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal
    ) {
    }

    public record OrderCreated(
        UUID orderId,
        String customerSubject,
        String status,
        String paymentStatus,
        BigDecimal total,
        String orderNumber,
        boolean guestOrder,
        String customerEmail,
        String customerName,
        String customerPhone,
        Instant createdAt,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal deliveryFee,
        String deliveryMethod,
        String deliveryAddressSummary,
        String paymentMethod,
        String paymentPlan,
        BigDecimal upfrontPaymentAmount,
        BigDecimal remainingDeliveryAmount,
        String paymentBankName,
        String paymentBankRib,
        String paymentBankBeneficiary,
        String trackingUrl,
        List<OrderLineItem> items
    ) {
        public OrderCreated(UUID orderId, String customerSubject, String status, String paymentStatus, BigDecimal total) {
            this(orderId, customerSubject, status, paymentStatus, total,
                    null, false, null, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null, null, null, List.of());
        }
    }

    public record OrderStatusChanged(
        UUID orderId,
        String customerSubject,
        String previousStatus,
        String status,
        String orderNumber,
        boolean guestOrder,
        String customerEmail,
        String customerName,
        String customerPhone,
        String trackingUrl
    ) {
        public OrderStatusChanged(UUID orderId, String customerSubject, String previousStatus, String status) {
            this(orderId, customerSubject, previousStatus, status, null, false, null, null, null, null);
        }
    }

    public record PaymentStatusChanged(
        UUID orderId,
        String customerSubject,
        String previousPaymentStatus,
        String paymentStatus,
        String orderNumber,
        boolean guestOrder,
        String customerEmail,
        String customerName,
        String customerPhone,
        String trackingUrl
    ) {
        public PaymentStatusChanged(UUID orderId, String customerSubject, String previousPaymentStatus, String paymentStatus) {
            this(orderId, customerSubject, previousPaymentStatus, paymentStatus, null, false, null, null, null, null);
        }
    }

    public record QuotationCreated(UUID quotationId, String customerSubject, String status) {
    }

    public record QuotationStatusChanged(
        UUID quotationId, String customerSubject, String previousStatus, String status
    ) {
    }

    public record MaintenanceCreated(UUID maintenanceId, String customerSubject, String status) {
    }

    public record MaintenanceAssigned(
        UUID maintenanceId, String customerSubject, String technicianSubject, String status
    ) {
    }

    public record MaintenanceStatusChanged(
        UUID maintenanceId, String customerSubject, String technicianSubject, String previousStatus, String status
    ) {
    }

    public record MaintenanceCompleted(
        UUID maintenanceId, String customerSubject, String technicianSubject, String status
    ) {
    }

    public record SupportMessageCreated(
        UUID conversationId, UUID messageId, String senderSubject, String recipientSubject, String senderRole
    ) {
    }

    public record SupportReplyCreated(
        UUID conversationId, UUID messageId, String senderSubject, String recipientSubject, String senderRole
    ) {
    }
}
