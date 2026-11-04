package ma.wwtrading.products.domain;

/**
 * Derived stock status. Always computed from the single availability formula
 * {@code available = onHand - reserved}; never stored as an independent value.
 */
public enum StockStatus {
    IN_STOCK,
    LOW_STOCK,
    OUT_OF_STOCK
}
