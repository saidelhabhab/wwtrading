package ma.wwtrading.wishlists.domain;

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
        name = "wishlist_item",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_wishlist_item_wishlist_product",
                columnNames = {"wishlist_id", "product_reference"}
        )
)
public class WishlistItemEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wishlist_id", nullable = false)
    private WishlistEntity wishlist;

    @Column(name = "product_reference", nullable = false, length = 64)
    private String productReference;

    protected WishlistItemEntity() {
    }

    WishlistItemEntity(WishlistEntity wishlist, String productReference) {
        this.wishlist = wishlist;
        this.productReference = productReference;
    }

    public UUID getId() { return id; }
    public WishlistEntity getWishlist() { return wishlist; }
    public String getProductReference() { return productReference; }
}
