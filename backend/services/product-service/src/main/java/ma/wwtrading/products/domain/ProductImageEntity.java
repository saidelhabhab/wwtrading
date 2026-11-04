package ma.wwtrading.products.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "product_image",
    indexes = {
        @Index(name = "idx_product_image_product", columnList = "product_id"),
        @Index(name = "idx_product_image_product_order", columnList = "product_id, display_order, created_at")
    }
)
public class ProductImageEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_product_image_product"))
    private ProductEntity product;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
    private String storageKey;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductImageEntity() {
    }

    public ProductImageEntity(ProductEntity product, String storageKey, boolean main, int displayOrder) {
        this.product = Objects.requireNonNull(product, "Product must not be null");
        this.storageKey = requireStorageKey(storageKey);
        this.main = main;
        this.displayOrder = requireDisplayOrder(displayOrder);
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

    public void markMain() {
        main = true;
    }

    public void clearMain() {
        main = false;
    }

    public void changeDisplayOrder(int displayOrder) {
        this.displayOrder = requireDisplayOrder(displayOrder);
    }

    private static String requireStorageKey(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Image storage key must not be blank");
        }
        return value.trim();
    }

    private static int requireDisplayOrder(int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Image display order must be non-negative");
        }
        return value;
    }

    public UUID getId() { return id; }
    public ProductEntity getProduct() { return product; }
    public String getStorageKey() { return storageKey; }
    public boolean isMain() { return main; }
    public int getDisplayOrder() { return displayOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
