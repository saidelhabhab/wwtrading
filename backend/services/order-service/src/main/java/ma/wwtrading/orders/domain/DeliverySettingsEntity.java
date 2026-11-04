package ma.wwtrading.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "delivery_settings")
public class DeliverySettingsEntity {

    public static final String DEFAULT_ID = "DEFAULT";
    public static final BigDecimal DEFAULT_KENITRA_FEE = new BigDecimal("300.00");
    public static final BigDecimal DEFAULT_CASABLANCA_FEE = new BigDecimal("300.00");
    public static final BigDecimal DEFAULT_OTHER_FEE = new BigDecimal("400.00");
    public static final BigDecimal DEFAULT_FREE_THRESHOLD = new BigDecimal("20000.00");

    @Id
    @Column(name = "id", nullable = false, length = 32)
    private String id = DEFAULT_ID;

    @Column(name = "fees_enabled", nullable = false)
    private boolean feesEnabled = true;

    @Column(name = "kenitra_area_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal kenitraAreaFee = DEFAULT_KENITRA_FEE;

    @Column(name = "casablanca_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal casablancaFee = DEFAULT_CASABLANCA_FEE;

    @Column(name = "other_areas_fee", nullable = false, precision = 19, scale = 2)
    private BigDecimal otherAreasFee = DEFAULT_OTHER_FEE;

    @Column(name = "free_delivery_threshold", nullable = false, precision = 19, scale = 2)
    private BigDecimal freeDeliveryThreshold = DEFAULT_FREE_THRESHOLD;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DeliverySettingsEntity() {
    }

    public DeliverySettingsEntity(
            boolean feesEnabled,
            BigDecimal kenitraAreaFee,
            BigDecimal casablancaFee,
            BigDecimal otherAreasFee,
            BigDecimal freeDeliveryThreshold) {
        this.id = DEFAULT_ID;
        update(feesEnabled, kenitraAreaFee, casablancaFee, otherAreasFee, freeDeliveryThreshold);
    }

    public static DeliverySettingsEntity defaultSettings() {
        return new DeliverySettingsEntity(
                true,
                DEFAULT_KENITRA_FEE,
                DEFAULT_CASABLANCA_FEE,
                DEFAULT_OTHER_FEE,
                DEFAULT_FREE_THRESHOLD);
    }

    public void setFeesEnabled(boolean feesEnabled) {
        this.feesEnabled = feesEnabled;
        this.updatedAt = Instant.now();
    }

    public void update(
            boolean feesEnabled,
            BigDecimal kenitraAreaFee,
            BigDecimal casablancaFee,
            BigDecimal otherAreasFee,
            BigDecimal freeDeliveryThreshold) {
        this.feesEnabled = feesEnabled;
        if (kenitraAreaFee != null) {
            if (kenitraAreaFee.signum() < 0) {
                throw new IllegalArgumentException("Kenitra area delivery fee must be non-negative");
            }
            this.kenitraAreaFee = kenitraAreaFee.setScale(2, RoundingMode.HALF_UP);
        }
        if (casablancaFee != null) {
            if (casablancaFee.signum() < 0) {
                throw new IllegalArgumentException("Casablanca delivery fee must be non-negative");
            }
            this.casablancaFee = casablancaFee.setScale(2, RoundingMode.HALF_UP);
        }
        if (otherAreasFee != null) {
            if (otherAreasFee.signum() < 0) {
                throw new IllegalArgumentException("Other areas delivery fee must be non-negative");
            }
            this.otherAreasFee = otherAreasFee.setScale(2, RoundingMode.HALF_UP);
        }
        if (freeDeliveryThreshold != null) {
            if (freeDeliveryThreshold.signum() < 0) {
                throw new IllegalArgumentException("Free delivery threshold must be non-negative");
            }
            this.freeDeliveryThreshold = freeDeliveryThreshold.setScale(2, RoundingMode.HALF_UP);
        }
        this.updatedAt = Instant.now();
    }

    @PrePersist
    @PreUpdate
    void touchUpdatedAt() {
        this.updatedAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public boolean isFeesEnabled() {
        return feesEnabled;
    }

    public BigDecimal getKenitraAreaFee() {
        return kenitraAreaFee;
    }

    public BigDecimal getCasablancaFee() {
        return casablancaFee;
    }

    public BigDecimal getOtherAreasFee() {
        return otherAreasFee;
    }

    public BigDecimal getFreeDeliveryThreshold() {
        return freeDeliveryThreshold;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

