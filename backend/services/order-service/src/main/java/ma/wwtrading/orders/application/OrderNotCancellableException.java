package ma.wwtrading.orders.application;

import java.util.UUID;

public class OrderNotCancellableException extends RuntimeException {

    public OrderNotCancellableException(UUID orderId) {
        super("Order cannot be cancelled: " + orderId);
    }
}