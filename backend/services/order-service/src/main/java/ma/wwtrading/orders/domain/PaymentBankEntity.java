package ma.wwtrading.orders.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_bank")
public class PaymentBankEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "reference", nullable = false, unique = true, length = 100)
    private String reference;

    @Column(name = "bank_name", nullable = false, length = 150)
    private String bankName;

    @Column(name = "rib", nullable = false, length = 50)
    private String rib;

    @Column(name = "beneficiary", nullable = false, length = 150)
    private String beneficiary;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentBankEntity() {
    }

    public PaymentBankEntity(String reference, String bankName, String rib, String beneficiary, String imageUrl, boolean active) {
        this.reference = normalizeString(reference);
        this.bankName = normalizeString(bankName);
        this.rib = normalizeString(rib);
        this.beneficiary = normalizeString(beneficiary);
        this.imageUrl = imageUrl != null ? imageUrl.trim() : null;
        this.active = active;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public String getBankName() { return bankName; }
    public String getRib() { return rib; }
    public String getBeneficiary() { return beneficiary; }
    public String getImageUrl() { return imageUrl; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateDetails(String reference, String bankName, String rib, String beneficiary, String imageUrl) {
        this.reference = normalizeString(reference);
        this.bankName = normalizeString(bankName);
        this.rib = normalizeString(rib);
        this.beneficiary = normalizeString(beneficiary);
        this.imageUrl = imageUrl != null ? imageUrl.trim() : null;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    private static String normalizeString(String value) {
        return value != null ? value.trim() : "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PaymentBankEntity that = (PaymentBankEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

