package ma.wwtrading.orders.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestOrderItemRequest(
    @NotBlank @Size(max = 64) String productReference,
    @Min(1) int quantity
) {
}

