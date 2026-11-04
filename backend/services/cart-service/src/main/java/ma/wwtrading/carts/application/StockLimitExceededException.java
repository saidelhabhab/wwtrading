package ma.wwtrading.carts.application;

public class StockLimitExceededException extends RuntimeException {

    private final int availableQuantity;

    public StockLimitExceededException(String productReference, int availableQuantity) {
        super("Available quantity exceeded for product " + productReference);
        this.availableQuantity = availableQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
