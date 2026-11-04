package ma.wwtrading.wishlists.application;

import java.util.UUID;

public class WishlistItemNotFoundException extends RuntimeException {

    public WishlistItemNotFoundException(UUID itemId) {
        super("Wishlist item not found: " + itemId);
    }
}
