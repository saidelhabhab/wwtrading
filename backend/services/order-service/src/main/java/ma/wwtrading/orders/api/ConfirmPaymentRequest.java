package ma.wwtrading.orders.api;

import java.math.BigDecimal;

public record ConfirmPaymentRequest(
        BigDecimal amount) {
}

