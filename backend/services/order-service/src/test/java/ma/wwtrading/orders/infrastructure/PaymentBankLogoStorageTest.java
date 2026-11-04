package ma.wwtrading.orders.infrastructure;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentBankLogoStorageTest {

    @TempDir
    Path tempDir;

    private PaymentBankLogoStorage logoStorage;

    @BeforeEach
    void setUp() {
        logoStorage = new PaymentBankLogoStorage(tempDir.toString());
        logoStorage.initialize();
    }

    @Test
    void storesValidPngImageSuccessfully() throws IOException {
        byte[] imageBytes = createSampleImageBytes("png");
        MockMultipartFile file = new MockMultipartFile("logo", "bank-logo.png", "image/png", imageBytes);

        String path = logoStorage.store(file);

        assertThat(path).isNotNull();
        assertThat(path).startsWith("/api/orders/payment-banks/logos/");
        assertThat(path).endsWith(".png");
    }

    @Test
    void storesValidJpgImageSuccessfully() throws IOException {
        byte[] imageBytes = createSampleImageBytes("jpg");
        MockMultipartFile file = new MockMultipartFile("logo", "bank-logo.jpg", "image/jpeg", imageBytes);

        String path = logoStorage.store(file);

        assertThat(path).isNotNull();
        assertThat(path).endsWith(".jpg");
    }

    @Test
    void rejectsInvalidNonImageFile() {
        byte[] fakeContent = "<?php echo 'malicious'; ?>".getBytes();
        MockMultipartFile file = new MockMultipartFile("logo", "script.php", "text/plain", fakeContent);

        assertThatThrownBy(() -> logoStorage.store(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsOversizedFile() {
        byte[] largeContent = new byte[(int) (PaymentBankLogoStorage.MAX_BYTES + 100)];
        MockMultipartFile file = new MockMultipartFile("logo", "large-image.png", "image/png", largeContent);

        assertThatThrownBy(() -> logoStorage.store(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2 MB");
    }

    private byte[] createSampleImageBytes(String format) throws IOException {
        BufferedImage image = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, format, baos);
        return baos.toByteArray();
    }
}

