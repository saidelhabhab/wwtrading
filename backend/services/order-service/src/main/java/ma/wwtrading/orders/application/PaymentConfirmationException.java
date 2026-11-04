package ma.wwtrading.orders.application;

import java.util.UUID;

public class PaymentConfirmationException extends RuntimeException {

    public PaymentConfirmationException(UUID orderId) {
        super("Order payment cannot be confirmed: " + orderId);
    }
}