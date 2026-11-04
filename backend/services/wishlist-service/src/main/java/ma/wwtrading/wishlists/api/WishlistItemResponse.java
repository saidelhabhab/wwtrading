package ma.wwtrading.wishlists.api;

import java.util.UUID;

public record WishlistItemResponse(
    UUID id,
    String productReference
) {
}
