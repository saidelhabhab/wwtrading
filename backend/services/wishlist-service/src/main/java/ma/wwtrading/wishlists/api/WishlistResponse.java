package ma.wwtrading.wishlists.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record WishlistResponse(
    UUID id,
    List<WishlistItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {
}
