package ma.wwtrading.products.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record ProductImageResponse(
    UUID id,
    String imageUrl,
    boolean main,
    int displayOrder,
    Instant createdAt,
    Instant updatedAt
) {
}

record ReorderProductImagesRequest(
    @NotNull(message = "Image IDs are required")
    List<@NotNull(message = "Image ID is required") UUID> imageIds
) {
    ReorderProductImagesRequest {
        if (imageIds != null) {
            imageIds = List.copyOf(imageIds);
        }
    }
}
