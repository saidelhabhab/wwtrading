package ma.wwtrading.products.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class ProductImageStorage {

    private static final Set<String> SAFE_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final Path storageRoot;

    public ProductImageStorage(
        @Value("${product.image.storage.path:${PRODUCT_IMAGE_STORAGE_PATH:${java.io.tmpdir}/wwtrading/product-images}}")
        String storagePath
    ) {
        storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initialize() {
        try {
            Files.createDirectories(storageRoot);
            if (!Files.isDirectory(storageRoot) || !Files.isWritable(storageRoot)) {
                throw new IllegalStateException("Product image storage is not a writable directory");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Product image storage cannot be initialized", exception);
        }
    }

    public String store(UUID productId, String originalFilename, InputStream content) {
        if (productId == null || content == null) {
            throw new IllegalArgumentException("Product and image content are required");
        }

        String extension = safeExtension(originalFilename);
        Path productDirectory = safeResolve("products/" + productId);
        try {
            Files.createDirectories(productDirectory);
            assertInsideRoot(productDirectory.toRealPath());
            String filename = UUID.randomUUID() + "." + extension;
            Path target = productDirectory.resolve(filename).normalize();
            assertInsideRoot(target);
            Files.copy(content, target);
            return storageRoot.relativize(target).toString().replace('\\', '/');
        } catch (IOException exception) {
            throw new IllegalStateException("Product image could not be stored", exception);
        }
    }

    public Path resolve(String storageKey) {
        Path target = safeResolve(storageKey);
        try {
            Path realTarget = target.toRealPath();
            assertInsideRoot(realTarget);
            return realTarget;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Stored product image does not exist", exception);
        }
    }

    public void delete(String storageKey) {
        Path target = safeResolve(storageKey);
        try {
            Files.deleteIfExists(target);
            Path parent = target.getParent();
            if (parent != null && !parent.equals(storageRoot)) {
                try {
                    Files.delete(parent);
                } catch (IOException ignored) {
                    // A non-empty product directory must be retained.
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Product image could not be deleted", exception);
        }
    }

    private Path safeResolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Image storage key must not be blank");
        }
        Path relative = Path.of(storageKey);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Absolute image storage paths are forbidden");
        }
        Path target = storageRoot.resolve(relative).normalize();
        assertInsideRoot(target);
        return target;
    }

    private void assertInsideRoot(Path candidate) {
        if (!candidate.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Image storage path escapes the configured root");
        }
    }

    private static String safeExtension(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Image filename must include a supported extension");
        }
        String filename = Path.of(originalFilename).getFileName().toString();
        int separator = filename.lastIndexOf('.');
        String extension = separator < 0 ? "" : filename.substring(separator + 1).toLowerCase(Locale.ROOT);
        if (!SAFE_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Image filename extension is not supported");
        }
        return extension;
    }
}
