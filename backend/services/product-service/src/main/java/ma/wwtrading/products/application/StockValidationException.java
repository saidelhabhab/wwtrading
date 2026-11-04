package ma.wwtrading.products.application;

import ma.wwtrading.products.domain.StockInvariantViolationException;

public class StockValidationException extends RuntimeException {

    public StockValidationException(String message) {
        super(message);
    }

    public static StockValidationException insufficient(String message) {
        return new StockValidationException(message);
    }

    public static StockValidationException of(StockInvariantViolationException cause) {
        return new StockValidationException(cause.getMessage());
    }
}
