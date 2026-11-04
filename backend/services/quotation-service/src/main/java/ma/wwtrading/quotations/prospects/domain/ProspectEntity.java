package ma.wwtrading.quotations.prospects.domain;

import java.time.Instant;
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
@Table(name = "prospect")
public class ProspectEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "contact_name", nullable = false, length = 120)
    private String contactName;

    @Column(length = 30)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(length = 100)
    private String city;

    @Column(length = 160)
    private String activity;

    @Column(length = 80)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProspectStatus status = ProspectStatus.NEW;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "assigned_commercial_subject", nullable = false, length = 255)
    private String assignedCommercialSubject;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProspectEntity() {
    }

    public ProspectEntity(
            String companyName,
            String contactName,
            String phone,
            String email,
            String city,
            String activity,
            String source,
            ProspectStatus status,
            String notes,
            String assignedCommercialSubject) {
        this.companyName = companyName;
        this.contactName = contactName;
        this.phone = phone;
        this.email = email;
        this.city = city;
        this.activity = activity;
        this.source = source;
        this.status = status == null ? ProspectStatus.NEW : status;
        this.notes = notes;
        this.assignedCommercialSubject = assignedCommercialSubject;
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

    public void update(
            String companyName,
            String contactName,
            String phone,
            String email,
            String city,
            String activity,
            String source,
            ProspectStatus status,
            String notes) {
        this.companyName = companyName;
        this.contactName = contactName;
        this.phone = phone;
        this.email = email;
        this.city = city;
        this.activity = activity;
        this.source = source;
        this.status = status == null ? ProspectStatus.NEW : status;
        this.notes = notes;
    }

    public UUID getId() {
        return id;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getContactName() {
        return contactName;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCity() {
        return city;
    }

    public String getActivity() {
        return activity;
    }

    public String getSource() {
        return source;
    }

    public ProspectStatus getStatus() {
        return status;
    }

    public String getNotes() {
        return notes;
    }

    public String getAssignedCommercialSubject() {
        return assignedCommercialSubject;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
