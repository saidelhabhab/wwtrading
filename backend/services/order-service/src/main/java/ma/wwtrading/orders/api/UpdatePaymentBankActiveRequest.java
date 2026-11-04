package ma.wwtrading.orders.api;

import jakarta.validation.constraints.NotNull;

public record UpdatePaymentBankActiveRequest(
    @NotNull(message = "Active state is required")
    Boolean active
) {
}

