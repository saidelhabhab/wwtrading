package ma.wwtrading.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import ma.wwtrading.orders.api.ShippingAddressSnapshot;

@Entity
@Table(name = "customer_order")
public class OrderEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "order_number", length = 32)
    private String orderNumber;

    @Column(name = "customer_reference", nullable = false, length = 320)
    private String customerReference;

    @Column(name = "customer_subject", length = 64)
    private String customerSubject;

    @Column(name = "guest_order", nullable = false)
    private boolean guestOrder = false;

    @Column(name = "contact_full_name", length = 200)
    private String contactFullName;

    @Column(name = "contact_email", length = 320)
    private String contactEmail;

    @Column(name = "contact_phone", length = 32)
    private String contactPhone;

    @Column(name = "tracking_token_hash", length = 64)
    private String trackingTokenHash;

    @Column(name = "tracking_token_expires_at")
    private Instant trackingTokenExpiresAt;

    @Column(name = "tracking_token_revoked", nullable = false)
    private boolean trackingTokenRevoked = false;

    @Column(name = "tracking_url", length = 500)
    private String trackingUrl;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "quotation_id", unique = true)
    private UUID quotationId;

    @Column(name = "discount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discount = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "firstName", column = @Column(name = "shipping_first_name", length = 100)),
        @AttributeOverride(name = "lastName", column = @Column(name = "shipping_last_name", length = 100)),
        @AttributeOverride(name = "company", column = @Column(name = "shipping_company", length = 200)),
        @AttributeOverride(name = "phone", column = @Column(name = "shipping_phone", length = 32)),
        @AttributeOverride(name = "addressLine1", column = @Column(name = "shipping_address_line_1", length = 255)),
        @AttributeOverride(name = "addressLine2", column = @Column(name = "shipping_address_line_2", length = 255)),
        @AttributeOverride(name = "city", column = @Column(name = "shipping_city", length = 120)),
        @AttributeOverride(name = "postalCode", column = @Column(name = "shipping_postal_code", length = 32)),
        @AttributeOverride(name = "country", column = @Column(name = "shipping_country", length = 100))
    })
    private ShippingAddressSnapshot shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_method", nullable = false, length = 32)
    private DeliveryMethod deliveryMethod;

    @Column(name = "delivery_area", length = 32)
    private String deliveryArea;

    @Column(name = "delivery_fee_waived_reason", length = 32)
    private String deliveryFeeWaivedReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 32)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_plan", nullable = false, length = 32)
    private PaymentPlan paymentPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 32)
    private PaymentStatus paymentStatus;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItemEntity> items = new ArrayList<>();

    @Column(name = "order_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "delivery_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "amount_paid", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountPaid = BigDecimal.ZERO.setScale(2);

    @Column(name = "upfront_payment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal upfrontPaymentAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "remaining_delivery_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingDeliveryAmount = BigDecimal.ZERO.setScale(2);

    @Column(name = "first_installment_confirmed_at")
    private Instant firstInstallmentConfirmedAt;

    @Column(name = "second_installment_confirmed_at")
    private Instant secondInstallmentConfirmedAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "selected_bank_reference", length = 100)
    private String selectedBankReference;

    @Column(name = "payment_bank_id")
    private UUID paymentBankId;

    @Column(name = "payment_bank_name", length = 150)
    private String paymentBankName;

    @Column(name = "payment_bank_rib", length = 50)
    private String paymentBankRib;

    @Column(name = "payment_bank_beneficiary", length = 150)
    private String paymentBankBeneficiary;

    @Column(name = "payment_bank_logo_url", length = 500)
    private String paymentBankLogoUrl;

    @Column(name = "transfer_proof_reference", length = 255)
    private String transferProofReference;

    @Column(name = "second_transfer_proof_reference", length = 255)
    private String secondTransferProofReference;

    @Column(name = "second_installment_payment_method", length = 32)
    private String secondInstallmentPaymentMethod;

    @Column(name = "second_installment_confirmed_by", length = 100)
    private String secondInstallmentConfirmedBy;

    @Column(name = "first_transfer_proof_submitted_at")
    private Instant firstTransferProofSubmittedAt;

    @Column(name = "second_transfer_proof_submitted_at")
    private Instant secondTransferProofSubmittedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderEntity() {
    }

    public OrderEntity(String customerReference, OrderStatus status,
                      ShippingAddressSnapshot shippingAddress,
                      DeliveryMethod deliveryMethod,
                      PaymentMethod paymentMethod,
                      List<OrderItemEntity> items,
                      BigDecimal deliveryFee) {
        this(customerReference, status, shippingAddress, deliveryMethod, paymentMethod, items,
                deliveryFee, null, BigDecimal.ZERO.setScale(2), null, null);
    }

    public OrderEntity(String customerReference, OrderStatus status,
                      ShippingAddressSnapshot shippingAddress,
                      DeliveryMethod deliveryMethod,
                      PaymentMethod paymentMethod,
                      List<OrderItemEntity> items,
                      BigDecimal deliveryFee, UUID quotationId, BigDecimal discount) {
        this(customerReference, status, shippingAddress, deliveryMethod, paymentMethod, items,
                deliveryFee, quotationId, discount, null, null);
    }

    public OrderEntity(String customerReference, OrderStatus status,
                      ShippingAddressSnapshot shippingAddress,
                      DeliveryMethod deliveryMethod,
                      PaymentMethod paymentMethod,
                      List<OrderItemEntity> items,
                      BigDecimal deliveryFee,
                      UUID quotationId,
                      BigDecimal discount,
                      String deliveryArea,
                      String deliveryFeeWaivedReason) {
        this.customerReference = customerReference;
        this.orderNumber = generateDefaultOrderNumber(this.id);
        this.contactEmail = customerReference;
        if (shippingAddress != null) {
            String first = shippingAddress.firstName() != null ? shippingAddress.firstName().trim() : "";
            String last = shippingAddress.lastName() != null ? shippingAddress.lastName().trim() : "";
            String combined = (first + " " + last).trim();
            if (!combined.isEmpty()) {
                this.contactFullName = combined;
            }
            if (shippingAddress.phone() != null && !shippingAddress.phone().isBlank()) {
                this.contactPhone = shippingAddress.phone().trim();
            }
        }
        this.quotationId = quotationId;
        this.status = status;
        this.shippingAddress = shippingAddress;
        this.deliveryMethod = deliveryMethod;
        this.paymentMethod = paymentMethod;
        this.paymentStatus = PaymentStatus.UNPAID;
        if (deliveryFee == null || deliveryFee.signum() < 0) {
            throw new IllegalArgumentException("Delivery fee must be non-negative");
        }
        if (discount == null || discount.signum() < 0) {
            throw new IllegalArgumentException("Discount must be non-negative");
        }
        if (paymentMethod == PaymentMethod.PAY_ON_PICKUP && deliveryMethod != DeliveryMethod.PICKUP) {
            throw new IllegalArgumentException("Pay on pickup is only available for pickup orders");
        }
        this.discount = discount.setScale(2, RoundingMode.HALF_UP);
        this.deliveryFee = deliveryFee.setScale(2, RoundingMode.HALF_UP);
        BigDecimal lineSubtotal = items.stream()
            .map(OrderItemEntity::getSubtotal)
            .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        if (this.discount.compareTo(lineSubtotal) > 0) {
            throw new IllegalArgumentException("Discount exceeds line subtotal");
        }
        this.total = lineSubtotal.subtract(this.discount).add(this.deliveryFee).setScale(2, RoundingMode.HALF_UP);
        this.amountPaid = BigDecimal.ZERO.setScale(2);

        if (deliveryMethod == DeliveryMethod.PICKUP) {
            this.deliveryArea = deliveryArea != null ? deliveryArea : "PICKUP";
            this.deliveryFeeWaivedReason = deliveryFeeWaivedReason != null ? deliveryFeeWaivedReason : "PICKUP";
        } else {
            this.deliveryArea = deliveryArea;
            this.deliveryFeeWaivedReason = deliveryFeeWaivedReason;
        }

        if (paymentMethod == PaymentMethod.SPLIT_50_50) {
            this.paymentPlan = PaymentPlan.SPLIT_50_50;
            this.upfrontPaymentAmount = calculateFirstInstallment(this.total);
            this.remainingDeliveryAmount = calculateSecondInstallment(this.total, this.upfrontPaymentAmount);
        } else if (paymentMethod == PaymentMethod.BANK_TRANSFER) {
            this.paymentPlan = PaymentPlan.FULL_BANK_TRANSFER;
            this.upfrontPaymentAmount = this.total;
            this.remainingDeliveryAmount = BigDecimal.ZERO.setScale(2);
        } else if (paymentMethod == PaymentMethod.PAY_ON_PICKUP) {
            this.paymentPlan = PaymentPlan.PAY_ON_PICKUP;
            this.upfrontPaymentAmount = BigDecimal.ZERO.setScale(2);
            this.remainingDeliveryAmount = this.total;
        } else {
            this.paymentPlan = deliveryMethod == DeliveryMethod.PICKUP
                    ? PaymentPlan.PAY_ON_PICKUP
                    : PaymentPlan.CASH_ON_DELIVERY;
            this.upfrontPaymentAmount = BigDecimal.ZERO.setScale(2);
            this.remainingDeliveryAmount = this.total;
        }

        this.items.addAll(items);
        items.forEach(item -> item.attachTo(this));
    }

    public static BigDecimal calculateFirstInstallment(BigDecimal finalTotal) {
        if (finalTotal == null || finalTotal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return finalTotal.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal calculateSecondInstallment(BigDecimal finalTotal, BigDecimal firstInstallment) {
        if (finalTotal == null || finalTotal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        BigDecimal first = firstInstallment == null ? calculateFirstInstallment(finalTotal) : firstInstallment;
        return finalTotal.setScale(2, RoundingMode.HALF_UP).subtract(first).setScale(2, RoundingMode.HALF_UP);
    }

    public static String generateDefaultOrderNumber(UUID orderId) {
        UUID source = orderId != null ? orderId : UUID.randomUUID();
        return "WWT-" + source.toString().replace("-", "").substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (orderNumber == null || orderNumber.isBlank()) {
            orderNumber = generateDefaultOrderNumber(id);
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getOrderNumber() {
        return orderNumber != null && !orderNumber.isBlank() ? orderNumber : generateDefaultOrderNumber(id);
    }
    public String getCustomerReference() { return customerReference; }
    public String getCustomerSubject() { return customerSubject; }
    public boolean isGuestOrder() { return guestOrder; }
    public String getContactFullName() { return contactFullName; }
    public String getContactEmail() { return contactEmail != null ? contactEmail : customerReference; }
    public String getContactPhone() { return contactPhone; }
    public String getTrackingTokenHash() { return trackingTokenHash; }
    public String getTrackingUrl() { return trackingUrl; }
    public void assignTrackingUrl(String trackingUrl) { this.trackingUrl = trackingUrl; }
    public Instant getTrackingTokenExpiresAt() { return trackingTokenExpiresAt; }
    public boolean isTrackingTokenRevoked() { return trackingTokenRevoked; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getLinkedAt() { return linkedAt; }
    public UUID getQuotationId() { return quotationId; }
    public BigDecimal getDiscount() { return discount; }
    public OrderStatus getStatus() { return status; }
    public ShippingAddressSnapshot getShippingAddress() { return shippingAddress; }
    public DeliveryMethod getDeliveryMethod() { return deliveryMethod; }
    public String getDeliveryArea() { return deliveryArea; }
    public String getDeliveryFeeWaivedReason() { return deliveryFeeWaivedReason; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public PaymentPlan getPaymentPlan() { return paymentPlan; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public List<OrderItemEntity> getItems() { return List.copyOf(items); }
    public BigDecimal getTotal() { return total; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public BigDecimal getAmountPaid() { return amountPaid == null ? BigDecimal.ZERO.setScale(2) : amountPaid; }
    public BigDecimal getUpfrontPaymentAmount() { return upfrontPaymentAmount == null ? BigDecimal.ZERO.setScale(2) : upfrontPaymentAmount; }
    public BigDecimal getRemainingDeliveryAmount() { return remainingDeliveryAmount == null ? BigDecimal.ZERO.setScale(2) : remainingDeliveryAmount; }
    public BigDecimal getOutstandingBalance() {
        BigDecimal currentTotal = total == null ? BigDecimal.ZERO.setScale(2) : total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal paid = getAmountPaid().setScale(2, RoundingMode.HALF_UP);
        BigDecimal remaining = currentTotal.subtract(paid);
        return remaining.signum() < 0 ? BigDecimal.ZERO.setScale(2) : remaining;
    }
    public Instant getFirstInstallmentConfirmedAt() { return firstInstallmentConfirmedAt; }
    public Instant getSecondInstallmentConfirmedAt() { return secondInstallmentConfirmedAt; }
    public Instant getPaidAt() { return paidAt; }
    public String getSelectedBankReference() { return selectedBankReference; }
    public UUID getPaymentBankId() { return paymentBankId; }
    public String getPaymentBankName() { return paymentBankName; }
    public String getPaymentBankRib() { return paymentBankRib; }
    public String getPaymentBankBeneficiary() { return paymentBankBeneficiary; }
    public String getPaymentBankLogoUrl() { return paymentBankLogoUrl; }
    public String getTransferProofReference() { return transferProofReference; }
    public String getSecondTransferProofReference() { return secondTransferProofReference; }
    public String getSecondInstallmentPaymentMethod() { return secondInstallmentPaymentMethod; }
    public String getSecondInstallmentConfirmedBy() { return secondInstallmentConfirmedBy; }
    public Instant getFirstTransferProofSubmittedAt() { return firstTransferProofSubmittedAt; }
    public Instant getSecondTransferProofSubmittedAt() { return secondTransferProofSubmittedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean requiresBankTransfer() {
        return paymentMethod == PaymentMethod.BANK_TRANSFER || paymentMethod == PaymentMethod.SPLIT_50_50;
    }

    public void assignCustomerSubject(String subject) {
        if (subject == null || subject.isBlank()) throw new IllegalArgumentException("Customer subject must not be blank");
        customerSubject = subject;
    }

    public void markAsGuestOrder(String fullName, String email, String phone,
                                 String tokenHash, Instant tokenExpiresAt) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Guest email must not be blank");
        }
        if (tokenHash == null || tokenHash.length() != 64) {
            throw new IllegalArgumentException("Tracking token hash must be a 64-character SHA-256 hex digest");
        }
        this.guestOrder = true;
        this.customerSubject = null;
        this.contactFullName = fullName != null ? fullName.trim() : null;
        this.contactEmail = email.trim();
        this.contactPhone = phone != null ? phone.trim() : null;
        this.trackingTokenHash = tokenHash;
        this.trackingTokenExpiresAt = tokenExpiresAt;
        this.trackingTokenRevoked = false;
    }

    public void assignContactDetails(String fullName, String email, String phone) {
        if (fullName != null && !fullName.isBlank()) {
            this.contactFullName = fullName.trim();
        }
        if (email != null && !email.isBlank()) {
            this.contactEmail = email.trim();
        }
        if (phone != null && !phone.isBlank()) {
            this.contactPhone = phone.trim();
        }
    }

    public void assignIdempotencyKey(String key) {
        if (key != null && !key.isBlank()) {
            String trimmed = key.trim();
            this.idempotencyKey = trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
        }
    }

    public boolean linkToAccount(String verifiedSubject, Instant now) {
        if (verifiedSubject == null || verifiedSubject.isBlank()) {
            throw new IllegalArgumentException("Verified customer subject must not be blank");
        }
        if (!this.guestOrder) {
            return false;
        }
        if (this.customerSubject != null) {
            if (this.customerSubject.equals(verifiedSubject)) {
                return false;
            }
            throw new IllegalStateException("Guest order is already linked to another account");
        }
        this.customerSubject = verifiedSubject;
        this.linkedAt = now != null ? now : Instant.now();
        return true;
    }

    public void revokeTrackingToken() {
        this.trackingTokenRevoked = true;
    }

    public void rotateTrackingToken(String newTokenHash, Instant newExpiresAt) {
        if (newTokenHash == null || newTokenHash.length() != 64) {
            throw new IllegalArgumentException("Tracking token hash must be a 64-character SHA-256 hex digest");
        }
        this.trackingTokenHash = newTokenHash;
        this.trackingTokenExpiresAt = newExpiresAt;
        this.trackingTokenRevoked = false;
    }

    public boolean isTrackingTokenValid(String candidateTokenHash, Instant now) {
        if (candidateTokenHash == null || this.trackingTokenHash == null || this.trackingTokenRevoked) {
            return false;
        }
        Instant checkTime = now != null ? now : Instant.now();
        if (this.trackingTokenExpiresAt != null && checkTime.isAfter(this.trackingTokenExpiresAt)) {
            return false;
        }
        return java.security.MessageDigest.isEqual(
                this.trackingTokenHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                candidateTokenHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public void cancel() {
        if (status != OrderStatus.DRAFT || paymentStatus != PaymentStatus.UNPAID || getAmountPaid().signum() > 0) {
            throw new IllegalStateException("Order cannot be cancelled in its current state");
        }
        status = OrderStatus.CANCELLED;
    }

    public void confirm() {
        if (status != OrderStatus.DRAFT) {
            throw new IllegalStateException("Order cannot be confirmed in its current state");
        }
        status = OrderStatus.CONFIRMED;
    }

    public void startProcessing() {
        if (status != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("Order cannot start processing in its current state");
        }
        status = OrderStatus.PROCESSING;
    }

    public void markReady() {
        if (status != OrderStatus.PROCESSING) {
            throw new IllegalStateException("Order cannot be marked ready in its current state");
        }
        status = OrderStatus.READY;
    }

    public void complete() {
        if (status != OrderStatus.READY) {
            throw new IllegalStateException("Order cannot be completed in its current state");
        }
        status = OrderStatus.COMPLETED;
    }

    public void confirmPayment() {
        confirmPayment(null, null);
    }

    public void confirmPayment(BigDecimal explicitAmount) {
        confirmPayment(explicitAmount, null);
    }

    public void confirmPayment(BigDecimal explicitAmount, String confirmedBy) {
        if (status == OrderStatus.CANCELLED || paymentStatus == PaymentStatus.PAID) {
            throw new IllegalStateException("Order payment cannot be confirmed");
        }
        if (explicitAmount != null && explicitAmount.signum() <= 0) {
            throw new IllegalArgumentException("Payment amount must be strictly positive");
        }
        BigDecimal outstanding = getOutstandingBalance();
        if (explicitAmount != null && explicitAmount.setScale(2, RoundingMode.HALF_UP).compareTo(outstanding) > 0) {
            throw new IllegalArgumentException("Payment amount cannot exceed outstanding balance");
        }

        Instant now = Instant.now();
        if (paymentPlan == PaymentPlan.SPLIT_50_50 || paymentMethod == PaymentMethod.SPLIT_50_50) {
            if (paymentStatus == PaymentStatus.UNPAID) {
                BigDecimal expectedFirst = getUpfrontPaymentAmount();
                if (explicitAmount != null
                        && explicitAmount.setScale(2, RoundingMode.HALF_UP).compareTo(expectedFirst) != 0) {
                    throw new IllegalArgumentException("First installment amount must equal the 50% upfront amount: " + expectedFirst);
                }
                this.amountPaid = expectedFirst;
                this.firstInstallmentConfirmedAt = now;
                if (this.amountPaid.compareTo(this.total) < 0) {
                    this.paymentStatus = PaymentStatus.PARTIALLY_PAID;
                } else {
                    this.paymentStatus = PaymentStatus.PAID;
                    this.paidAt = now;
                }
                this.updatedAt = now;
                return;
            }
            if (paymentStatus == PaymentStatus.PARTIALLY_PAID) {
                if (explicitAmount != null
                        && explicitAmount.setScale(2, RoundingMode.HALF_UP).compareTo(outstanding) != 0) {
                    throw new IllegalArgumentException("Second installment amount must equal the remaining balance: " + outstanding);
                }
                this.amountPaid = this.total.setScale(2, RoundingMode.HALF_UP);
                this.secondInstallmentConfirmedAt = now;
                this.secondInstallmentConfirmedBy = (confirmedBy != null && !confirmedBy.isBlank()) ? confirmedBy.trim() : "ADMIN";
                this.paidAt = now;
                this.paymentStatus = PaymentStatus.PAID;
                this.updatedAt = now;
                return;
            }
            throw new IllegalStateException("Order payment cannot be confirmed");
        }

        if (paymentStatus != PaymentStatus.UNPAID) {
            throw new IllegalStateException("Order payment cannot be confirmed");
        }
        if (explicitAmount != null
                && explicitAmount.setScale(2, RoundingMode.HALF_UP).compareTo(outstanding) != 0) {
            throw new IllegalArgumentException("Payment amount must equal the full outstanding balance: " + outstanding);
        }
        this.amountPaid = this.total.setScale(2, RoundingMode.HALF_UP);
        this.firstInstallmentConfirmedAt = now;
        this.paidAt = now;
        this.paymentStatus = PaymentStatus.PAID;
        this.updatedAt = now;
    }

    public void selectSecondInstallmentMethod(String method) {
        if (paymentPlan != PaymentPlan.SPLIT_50_50 && paymentMethod != PaymentMethod.SPLIT_50_50) {
            throw new IllegalStateException("Second installment payment method can only be selected for split payment orders");
        }
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Cannot select payment method for cancelled order");
        }
        if (paymentStatus == PaymentStatus.UNPAID && firstInstallmentConfirmedAt == null) {
            throw new IllegalStateException("First installment must be confirmed before selecting second installment payment method");
        }
        if (paymentStatus == PaymentStatus.PAID || secondInstallmentConfirmedAt != null) {
            throw new IllegalStateException("Second installment is already confirmed");
        }
        if (method == null || (!method.equalsIgnoreCase("BANK_TRANSFER") && !method.equalsIgnoreCase("CASH"))) {
            throw new IllegalArgumentException("Method must be BANK_TRANSFER or CASH");
        }
        this.secondInstallmentPaymentMethod = method.trim().toUpperCase();
        this.updatedAt = Instant.now();
    }

    public void attachTransferProof(String reference) {
        if (!requiresBankTransfer()) {
            throw new IllegalStateException("Transfer proof is only valid for bank transfer orders");
        }
        transferProofReference = reference;
        firstTransferProofSubmittedAt = Instant.now();
        updatedAt = Instant.now();
    }

    public void attachSecondTransferProof(String reference) {
        if (!requiresBankTransfer() || (paymentPlan != PaymentPlan.SPLIT_50_50 && paymentMethod != PaymentMethod.SPLIT_50_50)) {
            throw new IllegalStateException("Second transfer proof is only valid for split payment orders");
        }
        if (paymentStatus == PaymentStatus.UNPAID && firstInstallmentConfirmedAt == null) {
            throw new IllegalStateException("First installment must be confirmed before attaching second transfer proof");
        }
        if (paymentStatus == PaymentStatus.PAID || secondInstallmentConfirmedAt != null) {
            throw new IllegalStateException("Order is already fully paid");
        }
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order is cancelled");
        }
        this.secondTransferProofReference = reference;
        this.secondTransferProofSubmittedAt = Instant.now();
        this.secondInstallmentPaymentMethod = "BANK_TRANSFER";
        this.updatedAt = Instant.now();
    }

    public void selectBank(PaymentBankEntity bank) {
        if (!requiresBankTransfer()) {
            throw new IllegalStateException("A bank is only valid for bank transfer orders");
        }
        if (bank == null) {
            throw new IllegalArgumentException("Bank entity cannot be null");
        }
        this.selectedBankReference = bank.getReference();
        this.paymentBankId = bank.getId();
        this.paymentBankName = bank.getBankName();
        this.paymentBankRib = bank.getRib();
        this.paymentBankBeneficiary = bank.getBeneficiary();
        this.paymentBankLogoUrl = bank.getImageUrl();
    }

    public void selectBank(String reference) {
        if (!requiresBankTransfer()) {
            throw new IllegalStateException("A bank is only valid for bank transfer orders");
        }
        selectedBankReference = reference;
    }
}
