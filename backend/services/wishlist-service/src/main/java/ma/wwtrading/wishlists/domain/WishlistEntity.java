package ma.wwtrading.wishlists.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "customer_wishlist",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_wishlist_customer_reference",
                columnNames = "customer_reference"
        )
)
public class WishlistEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_reference", nullable = false, length = 64)
    private String customerReference;

    @OneToMany(mappedBy = "wishlist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WishlistItemEntity> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WishlistEntity() {
    }

    public WishlistEntity(String customerReference) {
        this.customerReference = customerReference;
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

    public WishlistItemEntity addItem(String productReference) {
        if (productReference == null || productReference.isBlank()) {
            throw new IllegalArgumentException("Product reference is required");
        }
        boolean exists = items.stream()
            .anyMatch(item -> item.getProductReference().equals(productReference));
        if (exists) {
            throw new IllegalStateException("Product is already in the wishlist");
        }
        WishlistItemEntity item = new WishlistItemEntity(this, productReference);
        items.add(item);
        return item;
    }

    public Optional<WishlistItemEntity> findItem(UUID itemId) {
        return items.stream().filter(item -> item.getId().equals(itemId)).findFirst();
    }

    public boolean removeItem(UUID itemId) {
        return items.removeIf(item -> item.getId().equals(itemId));
    }

    public void clearItems() {
        items.clear();
    }

    public UUID getId() { return id; }
    public String getCustomerReference() { return customerReference; }
    public List<WishlistItemEntity> getItems() { return Collections.unmodifiableList(items); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
