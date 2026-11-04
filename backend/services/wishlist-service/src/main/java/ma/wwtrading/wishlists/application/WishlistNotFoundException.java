package ma.wwtrading.wishlists.application;

public class WishlistNotFoundException extends RuntimeException {

    public WishlistNotFoundException(String customerReference) {
        super("Wishlist not found for customer: " + customerReference);
    }
}
