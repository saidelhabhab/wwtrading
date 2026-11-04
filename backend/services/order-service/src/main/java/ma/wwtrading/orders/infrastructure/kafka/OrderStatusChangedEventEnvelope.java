package ma.wwtrading.orders.infrastructure.kafka;

import java.time.Instant;
import java.util.UUID;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads.OrderStatusChanged;
import ma.wwtrading.events.EventPayloads.PaymentStatusChanged;
import ma.wwtrading.orders.domain.OrderEntity;

/**
 * Builds the versioned ORDER_STATUS_CHANGED / PAYMENT_STATUS_CHANGED domain
 * event envelopes.
 */
public final class OrderStatusChangedEventEnvelope {

    private OrderStatusChangedEventEnvelope() {
    }

    public static DomainEvent<OrderStatusChanged> orderStatusChanged(OrderEntity order, Object previousStatus) {
        return orderStatusChanged(order, previousStatus, resolveTrackingUrl(order));
    }

    public static DomainEvent<OrderStatusChanged> orderStatusChanged(
            OrderEntity order, Object previousStatus, String trackingUrl) {
        return new DomainEvent<>(
                UUID.randomUUID(),
                ma.wwtrading.events.EventTypes.ORDER_STATUS_CHANGED,
                ma.wwtrading.events.EventTypes.VERSION_1,
                Instant.now(),
                null,
                "order-service",
                "ORDER",
                order.getId().toString(),
                order.getCustomerSubject(),
                new OrderStatusChanged(
                        order.getId(),
                        order.getCustomerSubject(),
                        previousStatus == null ? null : previousStatus.toString(),
                        order.getStatus().name(),
                        order.getOrderNumber(),
                        order.isGuestOrder(),
                        order.getContactEmail(),
                        OrderCreatedEventEnvelope.resolveRecipientName(order),
                        OrderCreatedEventEnvelope.resolveRecipientPhone(order),
                        trackingUrl
                )
        );
    }

    public static DomainEvent<PaymentStatusChanged> paymentStatusChanged(OrderEntity order, Object previousStatus) {
        return paymentStatusChanged(order, previousStatus, resolveTrackingUrl(order));
    }

    private static String resolveTrackingUrl(OrderEntity order) {
        if (order == null) {
            return null;
        }
        boolean isLinkedToCustomer = order.getCustomerSubject() != null
                && !order.getCustomerSubject().isBlank()
                && !order.getCustomerSubject().startsWith("guest:");
        if (isLinkedToCustomer) {
            return null;
        }
        return order.getTrackingUrl();
    }

    public static DomainEvent<PaymentStatusChanged> paymentStatusChanged(
            OrderEntity order, Object previousStatus, String trackingUrl) {
        return new DomainEvent<>(
                UUID.randomUUID(),
                ma.wwtrading.events.EventTypes.PAYMENT_STATUS_CHANGED,
                ma.wwtrading.events.EventTypes.VERSION_1,
                Instant.now(),
                null,
                "order-service",
                "ORDER",
                order.getId().toString(),
                order.getCustomerSubject(),
                new PaymentStatusChanged(
                        order.getId(),
                        order.getCustomerSubject(),
                        previousStatus == null ? null : previousStatus.toString(),
                        order.getPaymentStatus().name(),
                        order.getOrderNumber(),
                        order.isGuestOrder(),
                        order.getContactEmail(),
                        OrderCreatedEventEnvelope.resolveRecipientName(order),
                        OrderCreatedEventEnvelope.resolveRecipientPhone(order),
                        trackingUrl
                )
        );
    }
}
