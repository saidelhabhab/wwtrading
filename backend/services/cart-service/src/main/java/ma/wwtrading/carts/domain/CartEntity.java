package ma.wwtrading.carts.domain;

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
        name = "customer_cart",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_customer_cart_customer_reference",
                columnNames = "customer_reference"
        )
)
public class CartEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "customer_reference", nullable = false, length = 64)
    private String customerReference;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartItemEntity> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CartEntity() {
    }

    public CartEntity(String customerReference) {
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

    public CartItemEntity addOrMergeItem(String productReference, int quantity) {
        requirePositive(quantity);
        Optional<CartItemEntity> existingItem = items.stream()
            .filter(item -> item.getProductReference().equals(productReference))
            .findFirst();
        if (existingItem.isPresent()) {
            existingItem.get().increaseQuantity(quantity);
            return existingItem.get();
        }
        CartItemEntity item = new CartItemEntity(this, productReference, quantity);
        items.add(item);
        return item;
    }

    public Optional<CartItemEntity> findItem(UUID itemId) {
        return items.stream().filter(item -> item.getId().equals(itemId)).findFirst();
    }

    public Optional<CartItemEntity> findByProductReference(String productReference) {
        return items.stream().filter(item -> item.getProductReference().equals(productReference)).findFirst();
    }

    public boolean removeItem(UUID itemId) {
        return items.removeIf(item -> item.getId().equals(itemId));
    }

    public void clearItems() {
        items.clear();
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    public UUID getId() { return id; }
    public String getCustomerReference() { return customerReference; }
    public List<CartItemEntity> getItems() { return Collections.unmodifiableList(items); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
