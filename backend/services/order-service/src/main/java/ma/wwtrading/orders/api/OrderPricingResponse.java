package ma.wwtrading.orders.api;

import java.math.BigDecimal;
import ma.wwtrading.orders.application.DeliveryFeeProperties;
import ma.wwtrading.orders.domain.OrderEntity;

public record OrderPricingResponse(
        BigDecimal subtotal,
        BigDecimal deliveryFee,
        BigDecimal total,
        BigDecimal upfrontPaymentAmount,
        BigDecimal remainingDeliveryAmount,
        BigDecimal freeDeliveryThreshold,
        boolean deliveryFeesEnabled,
        String deliveryFeeWaivedReason,
        String deliveryArea) {

    public OrderPricingResponse(
            BigDecimal subtotal,
            BigDecimal deliveryFee,
            BigDecimal total) {
        this(
                subtotal,
                deliveryFee,
                total,
                OrderEntity.calculateFirstInstallment(total),
                OrderEntity.calculateSecondInstallment(total, OrderEntity.calculateFirstInstallment(total)),
                DeliveryFeeProperties.DEFAULT_FREE_DELIVERY_THRESHOLD,
                true,
                null,
                null
        );
    }
}
