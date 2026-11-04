package ma.wwtrading.products.infrastructure;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import ma.wwtrading.products.domain.ProductEntity;
import ma.wwtrading.products.domain.ProductImageEntity;

class ProductImagePersistenceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void modelsProductImageMetadataWithoutBinaryContent() {
        ProductEntity product = new ProductEntity("REF-IMAGE", "Product", null, BigDecimal.TEN);
        ProductImageEntity image = new ProductImageEntity(
            product,
            "products/" + product.getId() + "/generated.webp",
            true,
            0
        );

        assertThat(image.getId()).isNotNull();
        assertThat(image.getProduct()).isSameAs(product);
        assertThat(image.getStorageKey()).endsWith("generated.webp");
        assertThat(image.isMain()).isTrue();
        assertThat(image.getDisplayOrder()).isZero();

        image.clearMain();
        image.changeDisplayOrder(2);
        assertThat(image.isMain()).isFalse();
        assertThat(image.getDisplayOrder()).isEqualTo(2);
        assertThatThrownBy(() -> image.changeDisplayOrder(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsStorageAndPersistsUsingGeneratedSafeName() throws Exception {
        ProductImageStorage storage = new ProductImageStorage(temporaryDirectory.resolve("images").toString());
        storage.initialize();
        UUID productId = UUID.randomUUID();
        byte[] content = "generated-test-image".getBytes(StandardCharsets.UTF_8);

        String storageKey = storage.store(
            productId,
            "../../unsafe-original.WEBP",
            new ByteArrayInputStream(content)
        );
        Path storedFile = storage.resolve(storageKey);

        assertThat(storageKey).matches("products/" + productId + "/[0-9a-f-]{36}\\.webp");
        assertThat(storedFile).startsWith(temporaryDirectory.toRealPath());
        assertThat(Files.readAllBytes(storedFile)).isEqualTo(content);
        assertThat(storedFile.getFileName().toString()).doesNotContain("unsafe-original");
    }

    @Test
    void rejectsTraversalAbsolutePathsAndUnsupportedExtensions() {
        ProductImageStorage storage = new ProductImageStorage(temporaryDirectory.resolve("images").toString());
        storage.initialize();

        assertThatThrownBy(() -> storage.resolve("../outside.jpg"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.resolve(temporaryDirectory.resolve("outside.jpg").toString()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.store(
            UUID.randomUUID(), "malware.exe", new ByteArrayInputStream(new byte[] {1})
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
