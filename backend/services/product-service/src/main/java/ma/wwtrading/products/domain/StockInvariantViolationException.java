package ma.wwtrading.products.domain;

public class StockInvariantViolationException extends RuntimeException {

    public StockInvariantViolationException(String message) {
        super(message);
    }
}
