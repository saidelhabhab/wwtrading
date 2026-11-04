package ma.wwtrading.orders.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class TransferProofStorageTests {

    private static final byte[] PNG = Base64.getDecoder().decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @TempDir
    Path tempDirectory;

    private TransferProofStorage storage;

    @BeforeEach
    void setUp() {
        storage = new TransferProofStorage(tempDirectory.toString(), 128);
        storage.initialize();
    }

    @Test
    void storesValidPng() {
        String reference = storage.store(UUID.randomUUID(),
            new MockMultipartFile("file", "proof.png", "image/png", PNG));

        assertThat(reference).startsWith("orders/").endsWith(".png");
    }

    @Test
    void rejectsNonImageContent() {
        assertThatThrownBy(() -> storage.store(UUID.randomUUID(),
            new MockMultipartFile("file", "proof.png", "image/png", "not an image".getBytes())))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("valid image");
    }

    @Test
    void rejectsOversizedContent() {
        assertThatThrownBy(() -> storage.store(UUID.randomUUID(),
            new MockMultipartFile("file", "proof.png", "image/png", new byte[129])))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("too large");
    }

    @Test
    void loadsStoredResource() {
        UUID orderId = UUID.randomUUID();
        String reference = storage.store(orderId,
            new MockMultipartFile("file", "proof.png", "image/png", PNG));

        org.springframework.core.io.Resource resource = storage.loadAsResource(reference);

        assertThat(resource).isNotNull();
        assertThat(resource.exists()).isTrue();
        assertThat(resource.isReadable()).isTrue();
    }

    @Test
    void returnsNullForMissingOrEmptyResource() {
        assertThat(storage.loadAsResource(null)).isNull();
        assertThat(storage.loadAsResource("")).isNull();
        assertThat(storage.loadAsResource("orders/nonexistent/missing.png")).isNull();
    }
}