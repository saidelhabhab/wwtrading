package ma.wwtrading.quotations.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "quotation")
public class QuotationEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_reference", nullable = false, length = 64)
    private String customerReference;

    @Column(name = "customer_subject", length = 64)
    private String customerSubject;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "idempotency_payload_hash", length = 64)
    private String idempotencyPayloadHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private QuotationStatus status;

    @Column(name = "service_type", length = 64)
    private String serviceType;

    @Column(name = "requested_equipment", length = 255)
    private String requestedEquipment;

    @Column(name = "brand_model", length = 255)
    private String brandModel;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "full_name", length = 120)
    private String fullName;

    @Column(length = 120)
    private String company;

    @Column(length = 30)
    private String phone;

    @Column(length = 120)
    private String email;

    @Column(length = 100)
    private String city;

    @Column(name = "desired_timeline", length = 64)
    private String desiredTimeline;

    @Column(name = "estimated_budget", precision = 12, scale = 2)
    private BigDecimal estimatedBudget;

    @Column(name = "additional_message", columnDefinition = "TEXT")
    private String additionalMessage;

    @Column(name = "proposed_amount", precision = 12, scale = 2)
    private BigDecimal proposedAmount;

    @Column(name = "commercial_notes", columnDefinition = "TEXT")
    private String commercialNotes;

    @OneToMany(mappedBy = "quotation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<QuotationLineEntity> lines = new ArrayList<>();

    @Column(precision = 19, scale = 2)
    private BigDecimal discount;

    @Column(name = "delivery_fee", precision = 19, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(length = 200)
    private String subject;

    @Column(name = "request_description", length = 4000)
    private String requestDescription;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected QuotationEntity() {
    }

    public QuotationEntity(
        String customerReference,
        QuotationStatus status,
        String serviceType,
        String requestedEquipment,
        String brandModel,
        Integer quantity,
        String description,
        String fullName,
        String company,
        String phone,
        String email,
        String city,
        String desiredTimeline,
        BigDecimal estimatedBudget,
        String additionalMessage
    ) {
        this.customerReference = customerReference;
        this.status = status != null ? status : QuotationStatus.PENDING;
        this.serviceType = serviceType;
        this.requestedEquipment = requestedEquipment;
        this.brandModel = brandModel;
        this.quantity = quantity != null ? quantity : 1;
        this.description = description;
        this.fullName = fullName;
        this.company = company;
        this.phone = phone;
        this.email = email;
        this.city = city;
        this.desiredTimeline = desiredTimeline;
        this.estimatedBudget = estimatedBudget;
        this.additionalMessage = additionalMessage;
        this.subject = serviceType != null ? serviceType : "Demande de devis";
        this.requestDescription = description;
    }

    // Legacy constructor compatibility
    public QuotationEntity(
        String customerReference,
        QuotationStatus status,
        String subject,
        String requestDescription,
        String notes
    ) {
        this.customerReference = customerReference;
        this.status = status;
        this.subject = subject;
        this.requestDescription = requestDescription;
        this.notes = notes;
        this.serviceType = subject;
        this.description = requestDescription;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public void updateContent(String subject, String requestDescription, String notes) {
        this.subject = subject;
        this.requestDescription = requestDescription;
        this.notes = notes;
    }

    public void updateStatus(QuotationStatus newStatus, BigDecimal proposedAmount, String commercialNotes) {
        this.status = newStatus;
        if (proposedAmount != null) {
            this.proposedAmount = proposedAmount;
        }
        if (commercialNotes != null) {
            this.commercialNotes = commercialNotes;
        }
    }

    public void cancel() {
        this.status = QuotationStatus.CANCELLED;
    }

    public void addRequestedLine(QuotationLineEntity line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void propose(BigDecimal discount, BigDecimal deliveryFee, LocalDate validUntil, String commercialNote) {
        BigDecimal subtotal = getSubtotal();
        if (lines.isEmpty() || lines.stream().anyMatch(line -> line.getProposedLineTotal() == null)
                || discount == null || discount.signum() < 0 || discount.compareTo(subtotal) > 0
                || deliveryFee == null || deliveryFee.signum() < 0
                || validUntil == null || validUntil.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Invalid quotation proposal");
        }
        this.discount = discount;
        this.deliveryFee = deliveryFee;
        this.validUntil = validUntil;
        this.commercialNotes = commercialNote;
        this.proposedAmount = subtotal.subtract(discount).add(deliveryFee);
        this.status = QuotationStatus.QUOTED;
    }

    public void decide(QuotationStatus decision) {
        if (status != QuotationStatus.QUOTED
                || (decision != QuotationStatus.ACCEPTED && decision != QuotationStatus.REJECTED)
                || (decision == QuotationStatus.ACCEPTED
                    && validUntil != null && LocalDate.now().isAfter(validUntil))) {
            throw new IllegalStateException("Quotation is not awaiting a client decision");
        }
        status = decision;
    }

    public BigDecimal getSubtotal() {
        return lines.stream().map(QuotationLineEntity::getProposedLineTotal)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public UUID getId() { return id; }
    public String getCustomerReference() { return customerReference; }
    public String getCustomerSubject() { return customerSubject; }
    public QuotationStatus getStatus() { return status; }
    public String getServiceType() { return serviceType; }
    public String getRequestedEquipment() { return requestedEquipment; }
    public String getBrandModel() { return brandModel; }
    public Integer getQuantity() { return quantity; }
    public String getDescription() { return description; }
    public String getFullName() { return fullName; }
    public String getCompany() { return company; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getCity() { return city; }
    public String getDesiredTimeline() { return desiredTimeline; }
    public BigDecimal getEstimatedBudget() { return estimatedBudget; }
    public String getAdditionalMessage() { return additionalMessage; }
    public BigDecimal getProposedAmount() { return proposedAmount; }
    public List<QuotationLineEntity> getLines() { return lines; }
    public BigDecimal getDiscount() { return discount; }
    public BigDecimal getDeliveryFee() { return deliveryFee; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getCommercialNotes() { return commercialNotes; }
    public String getSubject() { return subject != null ? subject : serviceType; }
    public String getRequestDescription() { return requestDescription != null ? requestDescription : description; }
    public String getNotes() { return notes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void assignCustomerSubject(String subject) {
        if (subject != null && !subject.isBlank()) customerSubject = subject;
    }

    public String getIdempotencyKey() { return idempotencyKey; }

    public void assignIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            this.idempotencyKey = idempotencyKey.length() > 128 ? idempotencyKey.substring(0, 128) : idempotencyKey;
        }
    }

    public String getIdempotencyPayloadHash() { return idempotencyPayloadHash; }

    public void assignIdempotencyPayloadHash(String hash) {
        if (hash != null && !hash.isBlank()) {
            this.idempotencyPayloadHash = hash.length() > 64 ? hash.substring(0, 64) : hash;
        }
    }
}
