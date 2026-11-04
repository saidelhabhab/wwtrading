package ma.wwtrading.products.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Audit trail of every stock change.
 *
 * <p>{@code quantity} is always strictly positive. The direction of the change is given by the
 * movement type, and for {@link StockMovementType#ADJUST} by {@code onHandDelta} (signed).</p>
 */
@Entity
@Table(name = "stock_movement")
public class StockMovementEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "stock_id", nullable = false)
    private UUID stockId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StockMovementType type;

    @Column(nullable = false)
    private int quantity;

    /** Signed on-hand delta (null for RESERVE / RELEASE which do not change on-hand stock). */
    @Column(name = "on_hand_delta")
    private Integer onHandDelta;

    @Column(length = 150)
    private String reference;

    @Column(length = 255)
    private String reason;

    @Column(name = "actor", nullable = false, length = 150)
    private String actor;

    /** Idempotency key for order-driven operations (e.g. ORDER:{id}:RESERVE). Unique. */
    @Column(name = "idempotency_key", length = 150, unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockMovementEntity() {
    }

    public StockMovementEntity(
        UUID stockId,
        UUID productId,
        StockMovementType type,
        int quantity,
        Integer onHandDelta,
        String reference,
        String reason,
        String actor,
        String idempotencyKey
    ) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Movement quantity must be positive");
        }
        this.stockId = stockId;
        this.productId = productId;
        this.type = type;
        this.quantity = quantity;
        this.onHandDelta = onHandDelta;
        this.reference = normalize(reference);
        this.reason = normalize(reason);
        this.actor = actor;
        this.idempotencyKey = idempotencyKey;
    }

    @PrePersist
    void beforeInsert() {
        createdAt = Instant.now();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public UUID getId() { return id; }
    public UUID getStockId() { return stockId; }
    public UUID getProductId() { return productId; }
    public StockMovementType getType() { return type; }
    public int getQuantity() { return quantity; }
    public Integer getOnHandDelta() { return onHandDelta; }
    public String getReference() { return reference; }
    public String getReason() { return reason; }
    public String getActor() { return actor; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
}
