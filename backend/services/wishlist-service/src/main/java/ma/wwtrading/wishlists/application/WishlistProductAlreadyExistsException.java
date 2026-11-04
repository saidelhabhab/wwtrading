package ma.wwtrading.wishlists.application;

public class WishlistProductAlreadyExistsException extends RuntimeException {

    public WishlistProductAlreadyExistsException(String productReference) {
        super("Product already exists in wishlist: " + productReference);
    }
}
