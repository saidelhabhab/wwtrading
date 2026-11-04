package ma.wwtrading.orders.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "customer_order_item")
public class OrderItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private OrderEntity order;

    @Column(name = "product_reference", nullable = false, length = 64)
    private String productReference;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;

    protected OrderItemEntity() {
    }

    public OrderItemEntity(String productReference, int quantity, BigDecimal unitPrice) {
        this(productReference, null, quantity, unitPrice);
    }

    public OrderItemEntity(String productReference, String productName, int quantity, BigDecimal unitPrice) {
        if (productReference == null || productReference.isBlank() || quantity <= 0
            || unitPrice == null || unitPrice.signum() < 0) {
            throw new IllegalArgumentException("Invalid order item");
        }
        this.productReference = productReference.trim();
        this.productName = productName != null && !productName.isBlank() ? productName.trim() : null;
        this.quantity = quantity;
        this.unitPrice = unitPrice.setScale(2, RoundingMode.UNNECESSARY);
        this.subtotal = this.unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

    void attachTo(OrderEntity order) { this.order = order; }

    public UUID getId() { return id; }
    public String getProductReference() { return productReference; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getSubtotal() { return subtotal; }
}
