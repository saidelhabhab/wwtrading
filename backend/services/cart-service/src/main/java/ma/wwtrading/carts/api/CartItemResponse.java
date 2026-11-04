package ma.wwtrading.carts.api;

import java.util.UUID;

public record CartItemResponse(
    UUID id,
    String productReference,
    int quantity,
    Integer availableQuantity
) {
}
