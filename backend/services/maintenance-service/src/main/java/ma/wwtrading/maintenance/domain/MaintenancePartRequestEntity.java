package ma.wwtrading.maintenance.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "maintenance_part_requests")
public class MaintenancePartRequestEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "maintenance_id", nullable = false)
    private UUID maintenanceId;

    @Column(name = "part_name", nullable = false, length = 200)
    private String partName;

    @Column(name = "part_reference", length = 100)
    private String partReference;

    @Column(nullable = false)
    private int quantity;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MaintenancePartRequestStatus status = MaintenancePartRequestStatus.REQUESTED;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "reviewed_by", length = 120)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected MaintenancePartRequestEntity() {
    }

    public MaintenancePartRequestEntity(UUID maintenanceId, String partName, String partReference,
                                       int quantity, String reason, UUID requestedBy) {
        this.maintenanceId = Objects.requireNonNull(maintenanceId, "Maintenance ID must not be null");
        this.partName = Objects.requireNonNull(partName, "Part name must not be null").trim();
        if (this.partName.isBlank()) {
            throw new IllegalArgumentException("Part name must not be blank");
        }
        this.partReference = partReference != null && !partReference.isBlank() ? partReference.trim() : null;
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero");
        }
        this.quantity = quantity;
        this.reason = reason != null && !reason.isBlank() ? reason.trim() : null;
        this.requestedBy = Objects.requireNonNull(requestedBy, "RequestedBy must not be null");
    }

    @PrePersist
    void beforeInsert() {
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
    }

    public void approve(String reviewer) {
        transitionFrom(MaintenancePartRequestStatus.REQUESTED, MaintenancePartRequestStatus.APPROVED);
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    public void reject(String reviewer) {
        transitionFrom(MaintenancePartRequestStatus.REQUESTED, MaintenancePartRequestStatus.REJECTED);
        this.reviewedBy = reviewer;
        this.reviewedAt = Instant.now();
    }

    private void transitionFrom(MaintenancePartRequestStatus expected, MaintenancePartRequestStatus target) {
        if (status != expected) {
            throw new IllegalStateException("Invalid part request status transition: expected " + expected + " but was " + status);
        }
        status = target;
    }

    public UUID getId() { return id; }
    public UUID getMaintenanceId() { return maintenanceId; }
    public String getPartName() { return partName; }
    public String getPartReference() { return partReference; }
    public int getQuantity() { return quantity; }
    public String getReason() { return reason; }
    public MaintenancePartRequestStatus getStatus() { return status; }
    public UUID getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public String getReviewedBy() { return reviewedBy; }
    public Instant getReviewedAt() { return reviewedAt; }
}

