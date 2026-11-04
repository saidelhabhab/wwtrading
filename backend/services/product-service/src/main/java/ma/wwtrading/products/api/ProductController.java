package ma.wwtrading.products.api;

import java.net.URI;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import ma.wwtrading.products.application.ProductApplicationService;
import ma.wwtrading.products.domain.MachineCategory;
import ma.wwtrading.products.domain.ProductColorMode;
import ma.wwtrading.products.domain.ProductType;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductApplicationService service;

    public ProductController(ProductApplicationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a product", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        ProductResponse product = service.create(request);
        return ResponseEntity.created(URI.create("/api/products/" + product.id())).body(product);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product")
    public ProductResponse getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Get a product by reference")
    public ProductResponse getByReference(@PathVariable String reference) {
        return service.getByReference(reference);
    }

    @GetMapping
    @Operation(summary = "List products")
    public Page<ProductResponse> list(
        @RequestParam(required = false) String search,
        @RequestParam(required = false) ProductType type,
        @RequestParam(required = false) MachineCategory machineCategory,
        @RequestParam(required = false) ProductColorMode colorMode,
        @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return service.list(search, type, machineCategory, colorMode, pageable);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a product", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public ProductResponse update(
        @PathVariable UUID id,
        @Valid @RequestBody UpdateProductRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/activate")
    @Operation(summary = "Activate a product", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public ProductResponse activate(@PathVariable UUID id) {
        return service.activate(id);
    }

    @PostMapping("/{id}/deactivate")
    @Operation(summary = "Deactivate a product", security = @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME))
    public ProductResponse deactivate(@PathVariable UUID id) {
        return service.deactivate(id);
    }
}
