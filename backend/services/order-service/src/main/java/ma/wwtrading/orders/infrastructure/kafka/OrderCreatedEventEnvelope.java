package ma.wwtrading.orders.infrastructure.kafka;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.events.DomainEvent;
import ma.wwtrading.events.EventPayloads.OrderCreated;
import ma.wwtrading.events.EventPayloads.OrderLineItem;
import ma.wwtrading.orders.api.ShippingAddressSnapshot;
import ma.wwtrading.orders.domain.OrderEntity;

/**
 * Builds the versioned ORDER_CREATED domain event envelope from an order
 * aggregate.
 */
public final class OrderCreatedEventEnvelope {

    private OrderCreatedEventEnvelope() {
    }

    public static DomainEvent<OrderCreated> orderCreated(OrderEntity order) {
        return orderCreated(order, null);
    }

    public static DomainEvent<OrderCreated> orderCreated(OrderEntity order, String trackingUrl) {
        BigDecimal safeTotal = order.getTotal() == null ? BigDecimal.ZERO.setScale(2) : order.getTotal();
        BigDecimal safeFee = order.getDeliveryFee() == null ? BigDecimal.ZERO.setScale(2) : order.getDeliveryFee();
        BigDecimal safeDiscount = order.getDiscount() == null ? BigDecimal.ZERO.setScale(2) : order.getDiscount();
        BigDecimal subtotal = safeTotal.subtract(safeFee).add(safeDiscount);

        List<OrderLineItem> items = order.getItems().stream()
                .map(item -> new OrderLineItem(
                        item.getProductReference(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        item.getSubtotal()))
                .toList();

        return new DomainEvent<>(
                UUID.randomUUID(),
                ma.wwtrading.events.EventTypes.ORDER_CREATED,
                ma.wwtrading.events.EventTypes.VERSION_1,
                Instant.now(),
                null,
                "order-service",
                "ORDER",
                order.getId().toString(),
                order.getCustomerSubject(),
                new OrderCreated(
                        order.getId(),
                        order.getCustomerSubject(),
                        order.getStatus().name(),
                        order.getPaymentStatus().name(),
                        order.getTotal(),
                        order.getOrderNumber(),
                        order.isGuestOrder(),
                        order.getContactEmail(),
                        resolveRecipientName(order),
                        resolveRecipientPhone(order),
                        order.getCreatedAt() != null ? order.getCreatedAt() : Instant.now(),
                        subtotal,
                        safeDiscount,
                        safeFee,
                        order.getDeliveryMethod() != null ? order.getDeliveryMethod().name() : null,
                        formatShippingAddressSummary(order.getShippingAddress()),
                        order.getPaymentMethod() != null ? order.getPaymentMethod().name() : null,
                        order.getPaymentPlan() != null ? order.getPaymentPlan().name() : null,
                        order.getUpfrontPaymentAmount(),
                        order.getRemainingDeliveryAmount(),
                        order.getPaymentBankName(),
                        order.getPaymentBankRib(),
                        order.getPaymentBankBeneficiary(),
                        trackingUrl,
                        items
                )
        );
    }

    static String resolveRecipientName(OrderEntity order) {
        if (order.getContactFullName() != null && !order.getContactFullName().isBlank()) {
            return order.getContactFullName().trim();
        }
        ShippingAddressSnapshot addr = order.getShippingAddress();
        if (addr != null) {
            String first = addr.firstName() != null ? addr.firstName().trim() : "";
            String last = addr.lastName() != null ? addr.lastName().trim() : "";
            String full = (first + " " + last).trim();
            if (!full.isEmpty()) {
                return full;
            }
        }
        return "Client";
    }

    static String resolveRecipientPhone(OrderEntity order) {
        if (order.getContactPhone() != null && !order.getContactPhone().isBlank()) {
            return order.getContactPhone().trim();
        }
        ShippingAddressSnapshot addr = order.getShippingAddress();
        if (addr != null && addr.phone() != null && !addr.phone().isBlank()) {
            return addr.phone().trim();
        }
        return null;
    }

    static String formatShippingAddressSummary(ShippingAddressSnapshot addr) {
        if (addr == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        if (addr.addressLine1() != null && !addr.addressLine1().isBlank()) {
            sb.append(addr.addressLine1().trim());
        }
        if (addr.addressLine2() != null && !addr.addressLine2().isBlank()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(addr.addressLine2().trim());
        }
        if (addr.city() != null && !addr.city().isBlank()) {
            if (!sb.isEmpty()) sb.append(" — ");
            sb.append(addr.city().trim());
        }
        if (addr.postalCode() != null && !addr.postalCode().isBlank()) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append("(").append(addr.postalCode().trim()).append(")");
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
