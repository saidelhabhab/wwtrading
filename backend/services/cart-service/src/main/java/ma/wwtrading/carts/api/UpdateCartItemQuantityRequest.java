package ma.wwtrading.carts.api;

import jakarta.validation.constraints.Positive;

public record UpdateCartItemQuantityRequest(
    @Positive int quantity
) {
}
