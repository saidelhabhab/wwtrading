package ma.wwtrading.products.api;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import ma.wwtrading.products.application.ProductImageApplicationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/products/{productId}/images")
public class ProductImageController {

    private final ProductImageApplicationService service;

    public ProductImageController(ProductImageApplicationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List a product's images")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Images returned"),
        @ApiResponse(responseCode = "404", description = "Product not found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content)
    })
    public List<ProductImageResponse> list(@PathVariable UUID productId) {
        return service.list(productId);
    }

    @GetMapping("/{imageId}/content")
    @Operation(summary = "Read product image content")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Image content returned"),
        @ApiResponse(responseCode = "404", description = "Product or image not found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content)
    })
    public ResponseEntity<org.springframework.core.io.Resource> content(
        @PathVariable UUID productId,
        @PathVariable UUID imageId
    ) throws IOException {
        var image = service.content(productId, imageId);
        return ResponseEntity.ok()
            .contentType(image.mediaType())
            .contentLength(image.resource().contentLength())
            .cacheControl(CacheControl.noCache())
            .header("X-Content-Type-Options", "nosniff")
            .body(image.resource());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a product image", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Image uploaded"),
        @ApiResponse(responseCode = "400", description = "Invalid image", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient role", content = @Content),
        @ApiResponse(responseCode = "404", description = "Product not found", content = @Content),
        @ApiResponse(responseCode = "413", description = "Image too large", content = @Content),
        @ApiResponse(responseCode = "415", description = "Unsupported image type", content = @Content),
        @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content)
    })
    public ResponseEntity<ProductImageResponse> upload(
        @PathVariable UUID productId,
        @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(productId, file));
    }

    @PutMapping("/{imageId}/main")
    @Operation(summary = "Set the main product image", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Main image updated"),
        @ApiResponse(responseCode = "400", description = "Invalid identifier", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient role", content = @Content),
        @ApiResponse(responseCode = "404", description = "Product or image not found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content)
    })
    public ProductImageResponse setMain(@PathVariable UUID productId, @PathVariable UUID imageId) {
        return service.setMain(productId, imageId);
    }

    @DeleteMapping("/{imageId}")
    @Operation(summary = "Delete a product image", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Image deleted"),
        @ApiResponse(responseCode = "400", description = "Invalid identifier", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient role", content = @Content),
        @ApiResponse(responseCode = "404", description = "Product or image not found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content)
    })
    public ResponseEntity<Void> delete(@PathVariable UUID productId, @PathVariable UUID imageId) {
        service.delete(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    @Operation(summary = "Reorder all product images", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Images reordered"),
        @ApiResponse(responseCode = "400", description = "Invalid or incomplete image order", content = @Content),
        @ApiResponse(responseCode = "401", description = "Authentication required", content = @Content),
        @ApiResponse(responseCode = "403", description = "Insufficient role", content = @Content),
        @ApiResponse(responseCode = "404", description = "Product not found", content = @Content),
        @ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content)
    })
    public List<ProductImageResponse> reorder(
        @PathVariable UUID productId,
        @Valid @RequestBody ReorderProductImagesRequest request
    ) {
        return service.reorder(productId, request.imageIds());
    }
}
