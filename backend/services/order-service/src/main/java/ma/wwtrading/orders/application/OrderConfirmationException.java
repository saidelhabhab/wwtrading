package ma.wwtrading.orders.application;

import java.util.UUID;

public class OrderConfirmationException extends RuntimeException {

    public OrderConfirmationException(UUID orderId) {
        super("Order cannot be confirmed: " + orderId);
    }
}