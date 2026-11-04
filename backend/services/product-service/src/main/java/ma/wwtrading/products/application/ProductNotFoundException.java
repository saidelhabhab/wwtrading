package ma.wwtrading.products.application;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(UUID id) {
        super("Product not found: " + id);
    }

    public ProductNotFoundException(String reference) {
        super("Product not found: " + reference);
    }
}
