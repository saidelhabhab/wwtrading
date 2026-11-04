package ma.wwtrading.orders.application;

import java.util.UUID;

public class OrderProcessingException extends RuntimeException {

    public OrderProcessingException(UUID orderId) {
        super("Order cannot start processing: " + orderId);
    }
}