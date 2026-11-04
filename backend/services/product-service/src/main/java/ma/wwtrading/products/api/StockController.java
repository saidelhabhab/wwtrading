package ma.wwtrading.products.api;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import ma.wwtrading.products.application.StockApplicationService;
import ma.wwtrading.products.security.StockActors;

/**
 * Stock REST API. Read endpoints are open (consistent with the public product
 * catalogue reads); every mutation is protected by SecurityConfig rules:
 * ADMIN/MANAGER only.
 */
@RestController
@RequestMapping("/api/stock")
@Tag(name = "Stock")
public class StockController {

    private final StockApplicationService service;

    public StockController(StockApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get the stock balance of a product")
    public StockDtos.StockResponse getStock(@PathVariable UUID productId) {
        return service.getStock(productId);
    }

    @GetMapping
    @Operation(summary = "List stock balances")
    public Page<StockDtos.StockResponse> listStock(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return service.listStock(pageable);
    }

    @PostMapping("/{productId}/in")
    @Operation(summary = "Register inbound stock", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public StockDtos.StockResponse stockIn(
            @PathVariable UUID productId,
            @Valid @RequestBody StockDtos.StockOperationRequest request
    ) {
        return service.stockIn(productId, request, StockActors.currentActor());
    }

    @PostMapping("/{productId}/out")
    @Operation(summary = "Register outbound stock", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public StockDtos.StockResponse stockOut(
            @PathVariable UUID productId,
            @Valid @RequestBody StockDtos.StockOperationRequest request
    ) {
        return service.stockOut(productId, request, StockActors.currentActor());
    }

    @PostMapping("/{productId}/adjust")
    @Operation(summary = "Adjust on-hand stock after a physical count",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public StockDtos.StockResponse adjust(
            @PathVariable UUID productId,
            @Valid @RequestBody StockDtos.StockAdjustRequest request
    ) {
        return service.adjust(productId, request, StockActors.currentActor());
    }

    @PostMapping("/{productId}/reserve")
    @Operation(summary = "Reserve available stock", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public StockDtos.StockResponse reserve(
            @PathVariable UUID productId,
            @Valid @RequestBody StockDtos.StockOperationRequest request
    ) {
        return service.reserve(productId, request, StockActors.currentActor());
    }

    @PostMapping("/{productId}/release")
    @Operation(summary = "Release previously reserved stock",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public StockDtos.StockResponse release(
            @PathVariable UUID productId,
            @Valid @RequestBody StockDtos.StockOperationRequest request
    ) {
        return service.release(productId, request, StockActors.currentActor());
    }

    @PutMapping("/{productId}/threshold")
    @Operation(summary = "Update the low-stock threshold",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public ResponseEntity<Void> updateThreshold(
            @PathVariable UUID productId,
            @Valid @RequestBody StockDtos.StockThresholdRequest request
    ) {
        service.updateLowStockThreshold(productId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{productId}/movements")
    @Operation(summary = "Stock movement history of a product",
            security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public Page<StockDtos.StockMovementResponse> getMovements(
            @PathVariable UUID productId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable
    ) {
        return service.getMovements(productId, pageable);
    }
}
