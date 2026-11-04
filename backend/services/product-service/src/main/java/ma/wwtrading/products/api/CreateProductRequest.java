package ma.wwtrading.products.api;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ma.wwtrading.products.domain.MachineCategory;
import ma.wwtrading.products.domain.ProductColorMode;
import ma.wwtrading.products.domain.ProductType;

public record CreateProductRequest(
    @NotBlank @Size(max = 64) String reference,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 10000) String description,
    @NotNull @PositiveOrZero BigDecimal price,
    @NotNull ProductType type,
    @Size(max = 100) String brand,
    @Size(max = 150) String model,
    MachineCategory machineCategory,
    ProductColorMode colorMode,
    @PositiveOrZero Integer warrantyMonths,
    @PositiveOrZero Integer warrantyCopies
) {
    public CreateProductRequest(String reference, String name, String description, BigDecimal price) {
        this(reference, name, description, price, ProductType.PRINTER);
    }

    public CreateProductRequest(String reference, String name, String description, BigDecimal price, ProductType type) {
        this(reference, name, description, price, type, null, null, null, null, null, null);
    }
}
