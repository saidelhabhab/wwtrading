package ma.wwtrading.products.application;

public class ProductReferenceConflictException extends RuntimeException {

    public ProductReferenceConflictException(String productReference) {
        super("Product reference already exists: " + productReference);
    }
}
