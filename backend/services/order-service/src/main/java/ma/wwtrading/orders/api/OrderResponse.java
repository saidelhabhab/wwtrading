package ma.wwtrading.orders.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.OrderEntity;
import ma.wwtrading.orders.domain.OrderStatus;
import ma.wwtrading.orders.domain.PaymentMethod;
import ma.wwtrading.orders.domain.PaymentPlan;
import ma.wwtrading.orders.domain.PaymentStatus;

public record OrderResponse(
        UUID id,
        String orderNumber,
        String customerReference,
        String customerSubject,
        boolean guestOrder,
        String contactFullName,
        String contactEmail,
        String contactPhone,
        Instant linkedAt,
        ShippingAddressSnapshot shippingAddress,
        DeliveryMethod deliveryMethod,
        String deliveryArea,
        String deliveryFeeWaivedReason,
        PaymentMethod paymentMethod,
        PaymentPlan paymentPlan,
        PaymentStatus paymentStatus,
        List<OrderItemResponse> items,
        BigDecimal total,
        BigDecimal deliveryFee,
        BigDecimal amountPaid,
        BigDecimal upfrontPaymentAmount,
        BigDecimal remainingDeliveryAmount,
        Instant firstInstallmentConfirmedAt,
        Instant secondInstallmentConfirmedAt,
        Instant paidAt,
        String selectedBankReference,
        UUID paymentBankId,
        String paymentBankName,
        String paymentBankRib,
        String paymentBankBeneficiary,
        String paymentBankLogoUrl,
        String transferProofReference,
        String secondTransferProofReference,
        String secondInstallmentPaymentMethod,
        String secondInstallmentConfirmedBy,
        Instant firstTransferProofSubmittedAt,
        Instant secondTransferProofSubmittedAt,
        OrderStatus status,
        Instant createdAt,
        Instant updatedAt,
        UUID quotationId,
        BigDecimal discount,
        @JsonInclude(Include.NON_NULL) String trackingToken,
        @JsonInclude(Include.NON_NULL) String trackingUrl) {

    @com.fasterxml.jackson.annotation.JsonProperty("subtotal")
    public BigDecimal subtotal() {
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO.setScale(2) : total;
        BigDecimal safeFee = deliveryFee == null ? BigDecimal.ZERO.setScale(2) : deliveryFee;
        BigDecimal safeDiscount = discount == null ? BigDecimal.ZERO.setScale(2) : discount;
        return safeTotal.subtract(safeFee).add(safeDiscount);
    }

    @com.fasterxml.jackson.annotation.JsonProperty("outstandingBalance")
    public BigDecimal outstandingBalance() {
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO.setScale(2) : total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal safePaid = amountPaid == null ? BigDecimal.ZERO.setScale(2) : amountPaid.setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = safeTotal.subtract(safePaid);
        return remaining.signum() < 0 ? BigDecimal.ZERO.setScale(2) : remaining;
    }

    public OrderResponse withTracking(String rawTrackingToken, String publicTrackingUrl) {
        return new OrderResponse(
                id,
                orderNumber != null ? orderNumber : OrderEntity.generateDefaultOrderNumber(id),
                customerReference,
                customerSubject,
                guestOrder,
                contactFullName,
                contactEmail,
                contactPhone,
                linkedAt,
                shippingAddress,
                deliveryMethod,
                deliveryArea,
                deliveryFeeWaivedReason,
                paymentMethod,
                paymentPlan,
                paymentStatus,
                items,
                total,
                deliveryFee,
                amountPaid,
                upfrontPaymentAmount,
                remainingDeliveryAmount,
                firstInstallmentConfirmedAt,
                secondInstallmentConfirmedAt,
                paidAt,
                selectedBankReference,
                paymentBankId,
                paymentBankName,
                paymentBankRib,
                paymentBankBeneficiary,
                paymentBankLogoUrl,
                transferProofReference,
                secondTransferProofReference,
                secondInstallmentPaymentMethod,
                secondInstallmentConfirmedBy,
                firstTransferProofSubmittedAt,
                secondTransferProofSubmittedAt,
                status,
                createdAt,
                updatedAt,
                quotationId,
                discount,
                rawTrackingToken,
                publicTrackingUrl
        );
    }

    public OrderResponse(
            UUID id,
            String customerReference,
            ShippingAddressSnapshot shippingAddress,
            DeliveryMethod deliveryMethod,
            String deliveryArea,
            String deliveryFeeWaivedReason,
            PaymentMethod paymentMethod,
            PaymentPlan paymentPlan,
            PaymentStatus paymentStatus,
            List<OrderItemResponse> items,
            BigDecimal total,
            BigDecimal deliveryFee,
            BigDecimal amountPaid,
            BigDecimal upfrontPaymentAmount,
            BigDecimal remainingDeliveryAmount,
            Instant firstInstallmentConfirmedAt,
            Instant secondInstallmentConfirmedAt,
            Instant paidAt,
            String selectedBankReference,
            UUID paymentBankId,
            String paymentBankName,
            String paymentBankRib,
            String paymentBankBeneficiary,
            String paymentBankLogoUrl,
            String transferProofReference,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt,
            UUID quotationId,
            BigDecimal discount) {
        this(
                id,
                id != null ? OrderEntity.generateDefaultOrderNumber(id) : null,
                customerReference,
                null,
                false,
                null,
                customerReference,
                shippingAddress != null ? shippingAddress.phone() : null,
                null,
                shippingAddress,
                deliveryMethod,
                deliveryArea,
                deliveryFeeWaivedReason,
                paymentMethod,
                paymentPlan,
                paymentStatus,
                items,
                total,
                deliveryFee,
                amountPaid,
                upfrontPaymentAmount,
                remainingDeliveryAmount,
                firstInstallmentConfirmedAt,
                secondInstallmentConfirmedAt,
                paidAt,
                selectedBankReference,
                paymentBankId,
                paymentBankName,
                paymentBankRib,
                paymentBankBeneficiary,
                paymentBankLogoUrl,
                transferProofReference,
                null,
                null,
                null,
                null,
                null,
                status,
                createdAt,
                updatedAt,
                quotationId,
                discount,
                null,
                null
        );
    }

    public OrderResponse(
            UUID id,
            String customerReference,
            ShippingAddressSnapshot shippingAddress,
            DeliveryMethod deliveryMethod,
            PaymentMethod paymentMethod,
            PaymentStatus paymentStatus,
            List<OrderItemResponse> items,
            BigDecimal total,
            BigDecimal deliveryFee,
            String selectedBankReference,
            UUID paymentBankId,
            String paymentBankName,
            String paymentBankRib,
            String paymentBankBeneficiary,
            String paymentBankLogoUrl,
            String transferProofReference,
            OrderStatus status,
            Instant createdAt,
            Instant updatedAt,
            UUID quotationId,
            BigDecimal discount) {
        this(
                id,
                customerReference,
                shippingAddress,
                deliveryMethod,
                deliveryMethod == DeliveryMethod.PICKUP ? "PICKUP" : null,
                deliveryMethod == DeliveryMethod.PICKUP ? "PICKUP" : null,
                paymentMethod,
                resolvePlan(deliveryMethod, paymentMethod),
                paymentStatus,
                items,
                total,
                deliveryFee,
                paymentStatus == PaymentStatus.PAID && total != null ? total : BigDecimal.ZERO.setScale(2),
                resolveUpfront(paymentMethod, total),
                resolveRemaining(paymentMethod, total),
                null,
                null,
                null,
                selectedBankReference,
                paymentBankId,
                paymentBankName,
                paymentBankRib,
                paymentBankBeneficiary,
                paymentBankLogoUrl,
                transferProofReference,
                status,
                createdAt,
                updatedAt,
                quotationId,
                discount
        );
    }

    public OrderResponse(UUID id, String customerReference, ShippingAddressSnapshot shippingAddress,
            DeliveryMethod deliveryMethod, PaymentMethod paymentMethod, PaymentStatus paymentStatus,
            List<OrderItemResponse> items, BigDecimal total, BigDecimal deliveryFee,
            String selectedBankReference, UUID paymentBankId, String paymentBankName,
            String paymentBankRib, String paymentBankBeneficiary, String paymentBankLogoUrl,
            String transferProofReference, OrderStatus status, Instant createdAt, Instant updatedAt) {
        this(id, customerReference, shippingAddress, deliveryMethod, paymentMethod, paymentStatus, items,
                total, deliveryFee, selectedBankReference, paymentBankId, paymentBankName,
                paymentBankRib, paymentBankBeneficiary, paymentBankLogoUrl, transferProofReference,
                status, createdAt, updatedAt, null, BigDecimal.ZERO);
    }

    public OrderResponse(UUID id, String customerReference, ShippingAddressSnapshot shippingAddress,
            DeliveryMethod deliveryMethod, PaymentMethod paymentMethod,
            PaymentStatus paymentStatus, List<OrderItemResponse> items, BigDecimal total,
            BigDecimal deliveryFee, OrderStatus status, Instant createdAt, Instant updatedAt) {
        this(id, customerReference, shippingAddress, deliveryMethod, paymentMethod, paymentStatus, items,
                total, deliveryFee, null, null, null, null, null, null, null, status, createdAt, updatedAt,
                null, BigDecimal.ZERO);
    }

    public OrderResponse(UUID id, String customerReference, ShippingAddressSnapshot shippingAddress,
            DeliveryMethod deliveryMethod, PaymentMethod paymentMethod,
            PaymentStatus paymentStatus, List<OrderItemResponse> items, BigDecimal total,
            BigDecimal deliveryFee, String selectedBankReference, String transferProofReference,
            OrderStatus status, Instant createdAt, Instant updatedAt) {
        this(id, customerReference, shippingAddress, deliveryMethod, paymentMethod, paymentStatus, items,
                total, deliveryFee, selectedBankReference, null, null, null, null, null, transferProofReference,
                status, createdAt, updatedAt, null, BigDecimal.ZERO);
    }

    private static PaymentPlan resolvePlan(DeliveryMethod deliveryMethod, PaymentMethod paymentMethod) {
        if (paymentMethod == PaymentMethod.SPLIT_50_50) {
            return PaymentPlan.SPLIT_50_50;
        }
        if (paymentMethod == PaymentMethod.BANK_TRANSFER) {
            return PaymentPlan.FULL_BANK_TRANSFER;
        }
        if (paymentMethod == PaymentMethod.PAY_ON_PICKUP) {
            return PaymentPlan.PAY_ON_PICKUP;
        }
        return deliveryMethod == DeliveryMethod.PICKUP ? PaymentPlan.PAY_ON_PICKUP : PaymentPlan.CASH_ON_DELIVERY;
    }

    private static BigDecimal resolveUpfront(PaymentMethod paymentMethod, BigDecimal total) {
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO.setScale(2) : total;
        if (paymentMethod == PaymentMethod.SPLIT_50_50) {
            return OrderEntity.calculateFirstInstallment(safeTotal);
        }
        if (paymentMethod == PaymentMethod.BANK_TRANSFER) {
            return safeTotal;
        }
        return BigDecimal.ZERO.setScale(2);
    }

    private static BigDecimal resolveRemaining(PaymentMethod paymentMethod, BigDecimal total) {
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO.setScale(2) : total;
        if (paymentMethod == PaymentMethod.SPLIT_50_50) {
            return OrderEntity.calculateSecondInstallment(safeTotal, OrderEntity.calculateFirstInstallment(safeTotal));
        }
        if (paymentMethod == PaymentMethod.BANK_TRANSFER) {
            return BigDecimal.ZERO.setScale(2);
        }
        return safeTotal;
    }

    public record OrderItemResponse(
            UUID id,
            String productReference,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal subtotal) {
        public OrderItemResponse(UUID id, String productReference, int quantity, BigDecimal unitPrice, BigDecimal subtotal) {
            this(id, productReference, null, quantity, unitPrice, subtotal);
        }
    }
}
