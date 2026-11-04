package ma.wwtrading.orders.application;

import java.util.UUID;

public class OrderFulfillmentException extends RuntimeException {

    public OrderFulfillmentException(UUID orderId, String action) {
        super("Order cannot be " + action + ": " + orderId);
    }
}
