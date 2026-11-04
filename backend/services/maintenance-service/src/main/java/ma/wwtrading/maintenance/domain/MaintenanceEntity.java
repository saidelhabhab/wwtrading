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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "maintenance")
public class MaintenanceEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_reference", nullable = false, length = 64)
    private String customerReference;

    @Column(name = "customer_subject", length = 64)
    private String customerSubject;

    @Column(name = "client_reference", length = 100)
    private String clientReference;

    @Column(name = "product_reference", length = 64)
    private String productReference;

    @Column(name = "serial_number", length = 120)
    private String serialNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MaintenancePriority priority = MaintenancePriority.MEDIUM;

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "diagnosis", columnDefinition = "TEXT")
    private String diagnosis;

    @Column(name = "intervention_notes", columnDefinition = "TEXT")
    private String interventionNotes;

    @Column(name = "completion_summary", columnDefinition = "TEXT")
    private String completionSummary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MaintenanceStatus status = MaintenanceStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "client_confirmed_at")
    private Instant clientConfirmedAt;

    protected MaintenanceEntity() {
    }

    public MaintenanceEntity(String customerReference, String productReference, String subject, String description) {
        this(customerReference, productReference, null, MaintenancePriority.MEDIUM, subject, description);
    }

    public MaintenanceEntity(String customerReference, String productReference, String serialNumber,
                             MaintenancePriority priority, String subject, String description) {
        this(customerReference, null, productReference, serialNumber, priority, subject, description);
    }

    public MaintenanceEntity(String customerReference, String clientReference, String productReference,
                             String serialNumber, MaintenancePriority priority, String subject, String description) {
        this.customerReference = normalizeRequired(customerReference, "Customer reference");
        this.clientReference = normalizeOptional(clientReference);
        this.productReference = normalizeOptional(productReference);
        this.serialNumber = normalizeOptional(serialNumber);
        this.priority = priority != null ? priority : MaintenancePriority.MEDIUM;
        this.subject = normalizeRequired(subject, "Maintenance subject");
        this.description = normalizeRequired(description, "Maintenance description");
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

    /** Transition OPEN → SUBMITTED (client formally submits the request for manager review). */
    public void submit() {
        transitionFrom(MaintenanceStatus.OPEN, MaintenanceStatus.SUBMITTED);
    }

    /** Transition ASSIGNED → ACCEPTED (technician accepts assignment). */
    public void accept() {
        transitionFrom(MaintenanceStatus.ASSIGNED, MaintenanceStatus.ACCEPTED);
        acceptedAt = Instant.now();
    }

    /** Transition ASSIGNED → REJECTED (technician rejects assignment). */
    public void reject() {
        transitionFrom(MaintenanceStatus.ASSIGNED, MaintenanceStatus.REJECTED);
        rejectedAt = Instant.now();
    }

    /** Transition ACCEPTED → IN_PROGRESS (technician starts intervention). */
    public void start() {
        transitionFrom(MaintenanceStatus.ACCEPTED, MaintenanceStatus.IN_PROGRESS);
        startedAt = Instant.now();
    }

    /** Transition IN_PROGRESS → WAITING_FOR_PART (technician waits for approved spare part). */
    public void waitOnPart() {
        transitionFrom(MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.WAITING_FOR_PART);
    }

    /** Transition WAITING_FOR_PART → IN_PROGRESS (technician resumes work when part is available). */
    public void resumeFromPartWait() {
        transitionFrom(MaintenanceStatus.WAITING_FOR_PART, MaintenanceStatus.IN_PROGRESS);
    }

    /** Record or update diagnosis and intervention notes when status is IN_PROGRESS. */
    public void updateIntervention(String diagnosis, String interventionNotes) {
        if (status != MaintenanceStatus.IN_PROGRESS) {
            throw new IllegalStateException("Intervention details can only be updated when request is IN_PROGRESS");
        }
        this.diagnosis = normalizeOptional(diagnosis);
        this.interventionNotes = normalizeOptional(interventionNotes);
    }

    /** Transition IN_PROGRESS → COMPLETION_PENDING_CONFIRMATION (technician submits completion). */
    public void submitCompletion(String completionSummary, String finalInterventionNotes) {
        transitionFrom(MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.COMPLETION_PENDING_CONFIRMATION);
        this.completionSummary = normalizeOptional(completionSummary);
        if (finalInterventionNotes != null && !finalInterventionNotes.isBlank()) {
            this.interventionNotes = normalizeOptional(finalInterventionNotes);
        }
        this.completedAt = Instant.now();
    }

    /** Transition COMPLETION_PENDING_CONFIRMATION → COMPLETED (client confirms completion). */
    public void confirmCompletion() {
        transitionFrom(MaintenanceStatus.COMPLETION_PENDING_CONFIRMATION, MaintenanceStatus.COMPLETED);
        this.clientConfirmedAt = Instant.now();
    }

    public void complete() {
        complete(null);
    }

    public void complete(String interventionNotes) {
        if (status == MaintenanceStatus.COMPLETION_PENDING_CONFIRMATION) {
            confirmCompletion();
            return;
        }
        if (status != MaintenanceStatus.IN_PROGRESS && status != MaintenanceStatus.WAITING_FOR_PART) {
            throw new IllegalStateException("Invalid maintenance status transition to COMPLETED");
        }
        status = MaintenanceStatus.COMPLETED;
        this.completedAt = Instant.now();
        this.clientConfirmedAt = Instant.now();
        if (interventionNotes != null && !interventionNotes.isBlank()) {
            this.interventionNotes = normalizeOptional(interventionNotes);
        }
    }

    /** Cancel from OPEN or SUBMITTED status. */
    public void cancel() {
        if (status != MaintenanceStatus.OPEN && status != MaintenanceStatus.SUBMITTED) {
            throw new IllegalStateException("Invalid maintenance status transition: can only cancel OPEN or SUBMITTED requests");
        }
        status = MaintenanceStatus.CANCELLED;
    }

    /**
     * Assign/reassign a technician to this maintenance request.
     * Transitions OPEN, SUBMITTED, or REJECTED requests to ASSIGNED.
     */
    public void assignTechnician(UUID technicianId) {
        this.technicianId = Objects.requireNonNull(technicianId, "Technician ID must not be null");
        if (status == MaintenanceStatus.OPEN || status == MaintenanceStatus.SUBMITTED || status == MaintenanceStatus.REJECTED) {
            status = MaintenanceStatus.ASSIGNED;
            assignedAt = Instant.now();
        } else if (status == MaintenanceStatus.ASSIGNED) {
            assignedAt = Instant.now();
        }
    }

    public void unassignTechnician() {
        technicianId = null;
    }

    public void updatePriority(MaintenancePriority priority) {
        if (priority != null) {
            this.priority = priority;
        }
    }

    private void transitionFrom(MaintenanceStatus expected, MaintenanceStatus target) {
        if (status != expected) {
            throw new IllegalStateException("Invalid maintenance status transition: expected " + expected + " but was " + status);
        }
        status = target;
    }

    private static String normalizeRequired(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public UUID getId() { return id; }
    public String getCustomerReference() { return customerReference; }
    public String getCustomerSubject() { return customerSubject; }
    public String getClientReference() { return clientReference; }
    public String getProductReference() { return productReference; }
    public String getSerialNumber() { return serialNumber; }
    public MaintenancePriority getPriority() { return priority; }
    public UUID getTechnicianId() { return technicianId; }
    public String getSubject() { return subject; }
    public String getDescription() { return description; }
    public String getDiagnosis() { return diagnosis; }
    public String getInterventionNotes() { return interventionNotes; }
    public String getCompletionSummary() { return completionSummary; }
    public MaintenanceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getAssignedAt() { return assignedAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getClientConfirmedAt() { return clientConfirmedAt; }

    public void assignCustomerSubject(String subject) {
        if (subject != null && !subject.isBlank()) customerSubject = subject;
    }
}
