package ma.wwtrading.orders.infrastructure;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

@Component
public class TransferProofStorage {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("jpg", "jpeg", "png");

    private final Path storageRoot;
    private final long maxBytes;

    public TransferProofStorage(
        @Value("${order.transfer-proof.storage.path:${ORDER_TRANSFER_PROOF_STORAGE_PATH:${java.io.tmpdir}/wwtrading/transfer-proofs}}") String storagePath,
        @Value("${order.transfer-proof.max-bytes:${ORDER_TRANSFER_PROOF_MAX_BYTES:5242880}}") long maxBytes
    ) {
        this.storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    @PostConstruct
    public void initialize() {
        try {
            Files.createDirectories(storageRoot);
            if (!Files.isDirectory(storageRoot)) {
                throw new IllegalStateException("Transfer proof storage is not a directory");
            }
            Path testFile = storageRoot.resolve(".write-test-" + UUID.randomUUID());
            try {
                Files.write(testFile, new byte[0]);
                Files.deleteIfExists(testFile);
            } catch (Exception e) {
                throw new IllegalStateException("Transfer proof storage is not writable", e);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Transfer proof storage cannot be initialized", exception);
        }
    }

    public String store(UUID orderId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Transfer proof is required");
        }
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("Transfer proof is too large");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null && !originalFilename.isBlank()) {
            String lowerName = originalFilename.toLowerCase(Locale.ROOT);
            if (!lowerName.endsWith(".jpg") && !lowerName.endsWith(".jpeg") && !lowerName.endsWith(".png")) {
                throw new IllegalArgumentException("Only JPG, JPEG and PNG transfer proofs are supported");
            }
        }
        try {
            byte[] content = file.getBytes();
            String format = detectFormat(content);
            Path directory = storageRoot.resolve("orders").resolve(orderId.toString()).normalize();
            assertInsideRoot(directory);
            Files.createDirectories(directory);
            String storageKey = "orders/" + orderId + "/" + UUID.randomUUID() + "." + format;
            Path target = storageRoot.resolve(storageKey).normalize();
            assertInsideRoot(target);
            Files.write(target, content);
            return storageKey;
        } catch (IOException exception) {
            throw new IllegalStateException("Transfer proof could not be stored", exception);
        }
    }

    public Resource loadAsResource(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return null;
        }
        Path target = storageRoot.resolve(storageKey).normalize();
        assertInsideRoot(target);
        if (Files.exists(target) && Files.isReadable(target)) {
            return new FileSystemResource(target);
        }
        return null;
    }

    private String detectFormat(byte[] content) throws IOException {
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            var reader = ImageIO.getImageReaders(ImageIO.createImageInputStream(input));
            if (!reader.hasNext()) {
                throw new IllegalArgumentException("Transfer proof must be a valid image");
            }
            String format = reader.next().getFormatName().toLowerCase(Locale.ROOT);
            if (!SUPPORTED_FORMATS.contains(format)) {
                throw new IllegalArgumentException("Only JPG, JPEG and PNG transfer proofs are supported");
            }
            return format;
        }
    }

    private void assertInsideRoot(Path candidate) {
        if (!candidate.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Transfer proof path escapes the configured root");
        }
    }
}