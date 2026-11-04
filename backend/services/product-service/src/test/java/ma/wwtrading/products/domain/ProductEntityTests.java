package ma.wwtrading.products.domain;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

class ProductEntityTests {

    @Test
    void acceptsEveryMachineCategoryForPrinterProducts() {
        for (MachineCategory category : MachineCategory.values()) {
            ProductEntity product = extendedProduct(ProductType.PRINTER, category, null, null);

            assertThat(product.getMachineCategory()).isEqualTo(category);
        }
    }

    @Test
    void acceptsOptionalNonNegativeWarranty() {
        ProductEntity warranted = extendedProduct(ProductType.PRINTER, MachineCategory.DIGITAL_PRESS, 6, 10_000);
        ProductEntity withoutWarranty = extendedProduct(ProductType.PRINTER, null, null, null);

        assertThat(warranted.getWarrantyMonths()).isEqualTo(6);
        assertThat(warranted.getWarrantyCopies()).isEqualTo(10_000);
        assertThat(withoutWarranty.getWarrantyMonths()).isNull();
        assertThat(withoutWarranty.getWarrantyCopies()).isNull();
    }

    @Test
    void acceptsNonPrinterProductsWithoutMachineCategory() {
        assertThat(extendedProduct(ProductType.TONER, null, null, null).getType()).isEqualTo(ProductType.TONER);
        assertThat(extendedProduct(ProductType.CONSUMABLE, null, null, null).getType()).isEqualTo(ProductType.CONSUMABLE);
        assertThat(extendedProduct(ProductType.SPARE_PART, null, null, null).getType()).isEqualTo(ProductType.SPARE_PART);
    }

    @Test
    void rejectsMachineCategoryForNonPrinterProducts() {
        for (ProductType type : new ProductType[] {
            ProductType.TONER, ProductType.CONSUMABLE, ProductType.SPARE_PART
        }) {
            assertThatThrownBy(() -> extendedProduct(type, MachineCategory.DIGITAL_PRESS, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Machine category is only supported for printer products");
        }
    }

    @Test
    void rejectsNegativeWarrantyValues() {
        assertThatThrownBy(() -> extendedProduct(ProductType.PRINTER, null, -1, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Warranty months must be non-negative");
        assertThatThrownBy(() -> extendedProduct(ProductType.PRINTER, null, null, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Warranty copies must be non-negative");
    }

    @Test
    void preservesExistingConstructorsAndUpdatesExtendedFields() {
        ProductEntity legacy = new ProductEntity(" LEGACY ", " Legacy product ", null, BigDecimal.ONE);
        assertThat(legacy.getType()).isEqualTo(ProductType.PRINTER);
        assertThat(legacy.getBrand()).isNull();

        legacy.updateDetails(
            "UPDATED", "Updated product", "Description", BigDecimal.TEN,
            ProductType.PRINTER, " Konica Minolta ", " bizhub C360i ",
            MachineCategory.DIGITAL_PRESS, 6, 10_000
        );

        assertThat(legacy.getBrand()).isEqualTo("Konica Minolta");
        assertThat(legacy.getModel()).isEqualTo("bizhub C360i");
        assertThat(legacy.getMachineCategory()).isEqualTo(MachineCategory.DIGITAL_PRESS);
        assertThat(legacy.getWarrantyMonths()).isEqualTo(6);
        assertThat(legacy.getWarrantyCopies()).isEqualTo(10_000);
    }

    private ProductEntity extendedProduct(
        ProductType type,
        MachineCategory category,
        Integer warrantyMonths,
        Integer warrantyCopies
    ) {
        return new ProductEntity(
            "REF-001", "Product", null, BigDecimal.TEN, type,
            "Brand", "Model", category, warrantyMonths, warrantyCopies
        );
    }

    private ProductEntity coloredProduct(ProductType type, ProductColorMode colorMode) {
        return new ProductEntity(
            "REF-COLOR", "Product", null, BigDecimal.TEN, type,
            "Brand", "Model", null, colorMode, null, null
        );
    }

    @Test
    void acceptsColorModeForPrinters() {
        for (ProductColorMode colorMode : ProductColorMode.values()) {
            assertThat(coloredProduct(ProductType.PRINTER, colorMode).getColorMode()).isEqualTo(colorMode);
        }
    }

    @Test
    void rejectsColorModeForNonPrinterTypes() {
        for (ProductType type : List.of(ProductType.TONER, ProductType.CONSUMABLE, ProductType.SPARE_PART)) {
            for (ProductColorMode colorMode : ProductColorMode.values()) {
                assertThatThrownBy(() -> coloredProduct(type, colorMode))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Color mode is only supported for printer products");
            }
        }
    }

    @Test
    void allowsNullColorModeForEveryType() {
        for (ProductType type : ProductType.values()) {
            assertThat(coloredProduct(type, null).getColorMode()).isNull();
        }
    }
}
