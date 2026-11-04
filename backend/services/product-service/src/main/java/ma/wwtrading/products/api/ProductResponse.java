package ma.wwtrading.products.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import ma.wwtrading.products.domain.MachineCategory;
import ma.wwtrading.products.domain.ProductColorMode;
import ma.wwtrading.products.domain.ProductType;

public record ProductResponse(
    UUID id,
    String reference,
    String name,
    String description,
    BigDecimal price,
    ProductType type,
    String brand,
    String model,
    MachineCategory machineCategory,
    ProductColorMode colorMode,
    Integer warrantyMonths,
    Integer warrantyCopies,
    boolean active,
    Instant createdAt,
            Instant updatedAt,
            List<ProductImageResponse> images,
            Integer availableQuantity
    ) {
        /**
         * Compatibility field: this value is read from StockEntity by the application service;
         * it is not accepted by product create/update requests.
         */
    public ProductResponse(UUID id, String reference, String name, String description, BigDecimal price,
        boolean active, Instant createdAt, Instant updatedAt) {
        this(id, reference, name, description, price, ProductType.PRINTER, null, null, null, null, null, null,
            active, createdAt, updatedAt, List.of(), null);
    }
}
