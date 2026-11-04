package ma.wwtrading.products.application;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import jakarta.validation.Validation;
import ma.wwtrading.products.api.CreateProductRequest;
import ma.wwtrading.products.api.UpdateProductRequest;
import ma.wwtrading.products.domain.MachineCategory;
import ma.wwtrading.products.domain.ProductColorMode;
import ma.wwtrading.products.domain.ProductType;

class ProductMapperValidationTests {

    private final ProductMapper mapper = Mappers.getMapper(ProductMapper.class);

    @Test
    void mapsClientFieldsAndPreservesServerManagedDefaults() {
        var product = mapper.toEntity(new CreateProductRequest(
            "KM-C360I", "Konica Minolta C360i", "Professional multifunction printer",
            new BigDecimal("12500.00"), ProductType.PRINTER, "Konica Minolta",
            "bizhub C360i", MachineCategory.DIGITAL_PRESS, ProductColorMode.COLOR, 6, 10_000
        ));
        var response = mapper.toResponse(product);
        assertThat(response.reference()).isEqualTo("KM-C360I");
        assertThat(response.name()).isEqualTo("Konica Minolta C360i");
        assertThat(response.description()).isEqualTo("Professional multifunction printer");
        assertThat(response.price()).isEqualByComparingTo("12500.00");
        assertThat(response.type()).isEqualTo(ProductType.PRINTER);
        assertThat(response.brand()).isEqualTo("Konica Minolta");
        assertThat(response.model()).isEqualTo("bizhub C360i");
        assertThat(response.machineCategory()).isEqualTo(MachineCategory.DIGITAL_PRESS);
        assertThat(response.colorMode()).isEqualTo(ProductColorMode.COLOR);
        assertThat(response.warrantyMonths()).isEqualTo(6);
        assertThat(response.warrantyCopies()).isEqualTo(10_000);
        assertThat(response.active()).isTrue();
        assertThat(response.createdAt()).isEqualTo(product.getCreatedAt());
        assertThat(response.updatedAt()).isEqualTo(product.getUpdatedAt());
    }

    @Test
    void validatesCreateAndUpdateCatalogueFields() {
        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            var validator = validatorFactory.getValidator();
            var invalidCreate = new CreateProductRequest(
                " ", "", "x".repeat(10001), new BigDecimal("-1.00"), ProductType.PRINTER,
                "x".repeat(101), "x".repeat(151), null, null, -1, -1
            );
            assertThat(validator.validate(invalidCreate))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("reference", "name", "price", "description", "brand", "model",
                    "warrantyMonths", "warrantyCopies");

            assertThat(validator.validate(new CreateProductRequest(
                "KM-C360I", "Konica Minolta C360i", null, BigDecimal.ZERO
            ))).isEmpty();

            for (ProductType type : ProductType.values()) {
                assertThat(validator.validate(new CreateProductRequest(
                    "REF-" + type, "Product", null, BigDecimal.ZERO, type
                ))).isEmpty();
            }
        }
    }

    @Test
    void exposesExpectedDtoRecordComponents() {
        assertThat(CreateProductRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("reference", "name", "description", "price", "type", "brand", "model",
                "machineCategory", "colorMode", "warrantyMonths", "warrantyCopies");
        assertThat(UpdateProductRequest.class.getRecordComponents())
            .extracting(component -> component.getName())
            .containsExactly("reference", "name", "description", "price", "type", "brand", "model",
                "machineCategory", "colorMode", "warrantyMonths", "warrantyCopies");
    }
}