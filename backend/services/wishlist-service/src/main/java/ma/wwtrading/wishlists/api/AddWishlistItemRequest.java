package ma.wwtrading.wishlists.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddWishlistItemRequest(
    @NotBlank @Size(max = 64) String productReference
) {
}
