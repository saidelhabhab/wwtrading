package ma.wwtrading.products.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Real stock balance for a product.
 *
 * <p>Availability is always derived: {@code available = onHand - reserved}.
 * It is never stored as an independent writable quantity.</p>
 */
@Entity
@Table(name = "stock")
public class StockEntity {

    public static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "on_hand_quantity", nullable = false)
    private int onHandQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold = DEFAULT_LOW_STOCK_THRESHOLD;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StockEntity() {
    }

    public StockEntity(UUID productId, int onHandQuantity, int lowStockThreshold) {
        if (productId == null) {
            throw new IllegalArgumentException("Product id must not be null");
        }
        if (onHandQuantity < 0) {
            throw new StockInvariantViolationException("On-hand quantity cannot be negative");
        }
        if (lowStockThreshold < 0) {
            throw new StockInvariantViolationException("Low stock threshold cannot be negative");
        }
        this.productId = productId;
        this.onHandQuantity = onHandQuantity;
        this.reservedQuantity = 0;
        this.lowStockThreshold = lowStockThreshold;
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

    /** Adds physical stock (purchase / initial stock). */
    public void increase(int quantity) {
        requirePositive(quantity);
        onHandQuantity = Math.addExact(onHandQuantity, quantity);
    }

    /** Removes physical stock; rejects when insufficient on-hand stock. */
    public void decrease(int quantity) {
        requirePositive(quantity);
        if (quantity > onHandQuantity) {
            throw new InsufficientStockException(
                "Insufficient on-hand stock: requested " + quantity + ", on hand " + onHandQuantity);
        }
        onHandQuantity -= quantity;
        if (reservedQuantity > onHandQuantity) {
            throw new StockInvariantViolationException("Reserved quantity cannot exceed on-hand quantity");
        }
    }

    /** Moves quantity from available to reserved; rejects when available stock is insufficient. */
    public void reserve(int quantity) {
        requirePositive(quantity);
        if (availableQuantity() < quantity) {
            throw new InsufficientStockException(
                "Insufficient available stock: requested " + quantity + ", available " + availableQuantity());
        }
        reservedQuantity += quantity;
    }

    /** Releases previously reserved quantity; rejects when releasing more than currently reserved. */
    public void release(int quantity) {
        requirePositive(quantity);
        if (quantity > reservedQuantity) {
            throw new StockInvariantViolationException(
                "Cannot release " + quantity + ", only " + reservedQuantity + " currently reserved");
        }
        reservedQuantity -= quantity;
    }

    /** Corrects on-hand stock after a physical count to an explicit target quantity. */
    public int adjustToTarget(int targetQuantity) {
        if (targetQuantity < 0) {
            throw new StockInvariantViolationException("Target quantity cannot be negative");
        }
        if (targetQuantity < reservedQuantity) {
            throw new StockInvariantViolationException(
                "Target on-hand quantity " + targetQuantity + " is below the reserved quantity " + reservedQuantity);
        }
        int delta = targetQuantity - onHandQuantity;
        onHandQuantity = targetQuantity;
        return delta;
    }

    /**
     * Atomic fulfilment of a previously reserved quantity (order completion):
     * reserved -= quantity and onHand -= quantity in one step, recorded as OUT
     * by the caller. Rejects when insufficient reserved or on-hand stock.
     */
    public void fulfillReserved(int quantity) {
        requirePositive(quantity);
        if (quantity > reservedQuantity) {
            throw new StockInvariantViolationException(
                "Cannot fulfill " + quantity + ", only " + reservedQuantity + " currently reserved");
        }
        if (quantity > onHandQuantity) {
            throw new InsufficientStockException(
                "Insufficient on-hand stock: requested " + quantity + ", on hand " + onHandQuantity);
        }
        reservedQuantity -= quantity;
        onHandQuantity -= quantity;
    }

    public void updateLowStockThreshold(int threshold) {
        if (threshold < 0) {
            throw new StockInvariantViolationException("Low stock threshold cannot be negative");
        }
        this.lowStockThreshold = threshold;
    }

    public int availableQuantity() {
        return onHandQuantity - reservedQuantity;
    }

    public boolean lowStock() {
        int available = availableQuantity();
        return available > 0 && available <= lowStockThreshold;
    }

    public boolean outOfStock() {
        return availableQuantity() <= 0;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new StockInvariantViolationException("Quantity must be positive");
        }
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public int getOnHandQuantity() { return onHandQuantity; }
    public int getReservedQuantity() { return reservedQuantity; }
    public int getLowStockThreshold() { return lowStockThreshold; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
