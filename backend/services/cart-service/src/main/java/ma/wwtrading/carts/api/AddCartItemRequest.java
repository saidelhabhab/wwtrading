package ma.wwtrading.carts.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AddCartItemRequest(
    @NotBlank @Size(max = 64) String productReference,
    @Positive int quantity
) {
}
