package ma.wwtrading.maintenance.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePartRequestPayload(
    @NotBlank(message = "Part name is required")
    @Size(max = 200, message = "Part name must not exceed 200 characters")
    String partName,

    @Size(max = 100, message = "Part reference must not exceed 100 characters")
    String partReference,

    @Min(value = 1, message = "Quantity must be at least 1")
    int quantity,

    String reason
) {}

