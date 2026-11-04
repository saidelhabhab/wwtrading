package ma.wwtrading.carts.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "cart_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_cart_item_cart_product",
                columnNames = {"cart_id", "product_reference"}
        )
)
public class CartItemEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private CartEntity cart;

    @Column(name = "product_reference", nullable = false, length = 64)
    private String productReference;

    @Column(nullable = false)
    private int quantity;

    protected CartItemEntity() {
    }

    public CartItemEntity(CartEntity cart, String productReference, int quantity) {
        this.cart = cart;
        this.productReference = productReference;
        this.quantity = quantity;
    }

    public void updateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.quantity = quantity;
    }

    void increaseQuantity(int increment) {
        long mergedQuantity = (long) quantity + increment;
        if (increment <= 0 || mergedQuantity > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Quantity is outside the supported range");
        }
        quantity = (int) mergedQuantity;
    }

    public UUID getId() { return id; }
    public CartEntity getCart() { return cart; }
    public String getProductReference() { return productReference; }
    public int getQuantity() { return quantity; }
}
