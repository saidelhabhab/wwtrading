package ma.wwtrading.carts.application;

import java.util.UUID;

public class CartItemNotFoundException extends RuntimeException {

    public CartItemNotFoundException(UUID itemId) {
        super("Cart item was not found: " + itemId);
    }
}
