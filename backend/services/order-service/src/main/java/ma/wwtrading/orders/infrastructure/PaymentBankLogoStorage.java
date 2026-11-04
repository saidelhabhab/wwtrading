package ma.wwtrading.orders.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class PaymentBankLogoStorage {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("jpg", "jpeg", "png", "webp");
    public static final long MAX_BYTES = 2 * 1024 * 1024; // 2 MB

    private final Path storageRoot;

    public PaymentBankLogoStorage(
        @Value("${wwtrading.payment-bank.logo.storage.path:${PAYMENT_BANK_LOGO_STORAGE_PATH:${java.io.tmpdir}/wwtrading/bank-logos}}") String storagePath
    ) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initialize() {
        try {
            Files.createDirectories(storageRoot);
            if (!Files.isDirectory(storageRoot)) {
                throw new IllegalStateException("Payment bank logo storage is not a directory");
            }
            Path testFile = storageRoot.resolve(".write-test-" + UUID.randomUUID());
            try {
                Files.write(testFile, new byte[0]);
                Files.deleteIfExists(testFile);
            } catch (Exception e) {
                throw new IllegalStateException("Payment bank logo storage is not writable", e);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Payment bank logo storage cannot be initialized", exception);
        }
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Le fichier est trop volumineux. La taille maximale est de 2 MB.");
        }
        try {
            byte[] content = file.getBytes();
            String format = detectFormat(content, file.getOriginalFilename());
            String fileName = UUID.randomUUID() + "." + format;
            Path target = storageRoot.resolve(fileName).normalize();
            assertInsideRoot(target);
            Files.write(target, content);
            return "/api/orders/payment-banks/logos/" + fileName;
        } catch (IOException exception) {
            throw new IllegalStateException("Le logo de la banque n'a pas pu être enregistré.", exception);
        }
    }

    public Resource loadAsResource(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }
        Path target = storageRoot.resolve(filename).normalize();
        assertInsideRoot(target);
        if (Files.exists(target) && Files.isReadable(target)) {
            return new FileSystemResource(target);
        }
        return null;
    }

    private String detectFormat(byte[] content, String originalFilename) throws IOException {
        String extFormat = getExtensionFormat(originalFilename);
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            var reader = ImageIO.getImageReaders(ImageIO.createImageInputStream(input));
            if (!reader.hasNext()) {
                // If ImageIO doesn't support webp natively on all JVMs, fall back to extension if webp
                if ("webp".equals(extFormat)) {
                    return "webp";
                }
                throw new IllegalArgumentException("Le fichier doit être une image valide (JPG, JPEG, PNG ou WebP).");
            }
            String format = reader.next().getFormatName().toLowerCase(Locale.ROOT);
            if ("jpeg".equals(format)) {
                format = "jpg";
            }
            if (!SUPPORTED_FORMATS.contains(format)) {
                if ("webp".equals(extFormat)) {
                    return "webp";
                }
                throw new IllegalArgumentException("Seuls les formats JPG, JPEG, PNG et WebP sont autorisés.");
            }
            return format;
        }
    }

    private String getExtensionFormat(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return SUPPORTED_FORMATS.contains(ext) ? ext : "";
    }

    private void assertInsideRoot(Path candidate) {
        if (!candidate.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Logo path escapes the configured root");
        }
    }
}

