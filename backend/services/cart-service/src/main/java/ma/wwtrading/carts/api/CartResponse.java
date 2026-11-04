package ma.wwtrading.carts.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
    UUID id,
    List<CartItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {
}
