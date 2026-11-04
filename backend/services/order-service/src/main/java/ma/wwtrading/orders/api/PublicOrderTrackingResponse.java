package ma.wwtrading.orders.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import ma.wwtrading.orders.domain.DeliveryMethod;
import ma.wwtrading.orders.domain.OrderStatus;
import ma.wwtrading.orders.domain.PaymentMethod;
import ma.wwtrading.orders.domain.PaymentPlan;
import ma.wwtrading.orders.domain.PaymentStatus;

/**
 * Non-sensitive public order summary returned by the token-authenticated guest tracking endpoint.
 * Internal database UUIDs, customer subjects, auth tokens, and full street/contact PII are omitted.
 */
public record PublicOrderTrackingResponse(
    String orderNumber,
    OrderStatus status,
    PaymentStatus paymentStatus,
    PaymentMethod paymentMethod,
    PaymentPlan paymentPlan,
    DeliveryMethod deliveryMethod,
    String deliveryArea,
    String deliveryFeeWaivedReason,
    BigDecimal subtotal,
    BigDecimal discount,
    BigDecimal deliveryFee,
    BigDecimal total,
    BigDecimal amountPaid,
    BigDecimal upfrontPaymentAmount,
    BigDecimal remainingDeliveryAmount,
    BigDecimal outstandingBalance,
    String paymentBankName,
    String paymentBankRib,
    String paymentBankBeneficiary,
    String paymentBankLogoUrl,
    boolean transferProofSubmitted,
    boolean secondTransferProofSubmitted,
    String secondInstallmentPaymentMethod,
    Instant firstInstallmentConfirmedAt,
    Instant secondInstallmentConfirmedAt,
    Instant secondTransferProofSubmittedAt,
    String shippingCity,
    String recipientFirstName,
    Instant createdAt,
    Instant updatedAt,
    List<PublicOrderTrackingItemResponse> items
) {
    public PublicOrderTrackingResponse(
        String orderNumber,
        OrderStatus status,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        PaymentPlan paymentPlan,
        DeliveryMethod deliveryMethod,
        String deliveryArea,
        String deliveryFeeWaivedReason,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal deliveryFee,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal upfrontPaymentAmount,
        BigDecimal remainingDeliveryAmount,
        BigDecimal outstandingBalance,
        String paymentBankName,
        String paymentBankRib,
        String paymentBankBeneficiary,
        String paymentBankLogoUrl,
        boolean transferProofSubmitted,
        boolean secondTransferProofSubmitted,
        String shippingCity,
        String recipientFirstName,
        Instant createdAt,
        Instant updatedAt,
        List<PublicOrderTrackingItemResponse> items
    ) {
        this(
            orderNumber, status, paymentStatus, paymentMethod, paymentPlan, deliveryMethod,
            deliveryArea, deliveryFeeWaivedReason, subtotal, discount, deliveryFee, total,
            amountPaid, upfrontPaymentAmount, remainingDeliveryAmount, outstandingBalance,
            paymentBankName, paymentBankRib, paymentBankBeneficiary, paymentBankLogoUrl,
            transferProofSubmitted, secondTransferProofSubmitted, null, null, null, null,
            shippingCity, recipientFirstName, createdAt, updatedAt, items
        );
    }

    public PublicOrderTrackingResponse(
        String orderNumber,
        OrderStatus status,
        PaymentStatus paymentStatus,
        PaymentMethod paymentMethod,
        PaymentPlan paymentPlan,
        DeliveryMethod deliveryMethod,
        String deliveryArea,
        String deliveryFeeWaivedReason,
        BigDecimal subtotal,
        BigDecimal discount,
        BigDecimal deliveryFee,
        BigDecimal total,
        BigDecimal amountPaid,
        BigDecimal upfrontPaymentAmount,
        BigDecimal remainingDeliveryAmount,
        BigDecimal outstandingBalance,
        String paymentBankName,
        String paymentBankRib,
        String paymentBankBeneficiary,
        String paymentBankLogoUrl,
        boolean transferProofSubmitted,
        String shippingCity,
        String recipientFirstName,
        Instant createdAt,
        Instant updatedAt,
        List<PublicOrderTrackingItemResponse> items
    ) {
        this(
            orderNumber, status, paymentStatus, paymentMethod, paymentPlan, deliveryMethod,
            deliveryArea, deliveryFeeWaivedReason, subtotal, discount, deliveryFee, total,
            amountPaid, upfrontPaymentAmount, remainingDeliveryAmount, outstandingBalance,
            paymentBankName, paymentBankRib, paymentBankBeneficiary, paymentBankLogoUrl,
            transferProofSubmitted, false, null, null, null, null,
            shippingCity, recipientFirstName, createdAt, updatedAt, items
        );
    }

    public record PublicOrderTrackingItemResponse(
        String productReference,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
    ) {
    }
}
