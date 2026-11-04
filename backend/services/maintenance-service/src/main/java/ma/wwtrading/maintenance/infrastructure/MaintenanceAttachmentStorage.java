package ma.wwtrading.maintenance.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class MaintenanceAttachmentStorage {

    private final Path storageRoot;

    public MaintenanceAttachmentStorage(
        @Value("${maintenance.attachment.storage.path:${MAINTENANCE_ATTACHMENT_STORAGE_PATH:${java.io.tmpdir}/wwtrading/maintenance-attachments}}")
        String storagePath
    ) {
        storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void initialize() {
        try {
            Files.createDirectories(storageRoot);
            if (!Files.isDirectory(storageRoot) || !Files.isWritable(storageRoot)) {
                throw new IllegalStateException("Maintenance attachment storage is not writable");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Maintenance attachment storage cannot be initialized", exception);
        }
    }

    public String store(UUID maintenanceId, String extension, InputStream content) {
        Path directory = safeResolve("maintenance/" + maintenanceId);
        try {
            Files.createDirectories(directory);
            String filename = UUID.randomUUID() + "." + extension;
            Path target = directory.resolve(filename).normalize();
            assertInsideRoot(target);
            Files.copy(content, target);
            return storageRoot.relativize(target).toString().replace('\\', '/');
        } catch (IOException exception) {
            throw new IllegalStateException("Maintenance attachment could not be stored", exception);
        }
    }

    public Path resolve(String storageKey) {
        Path target = safeResolve(storageKey);
        try {
            Path realTarget = target.toRealPath();
            assertInsideRoot(realTarget);
            return realTarget;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Maintenance attachment does not exist", exception);
        }
    }

    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(safeResolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Maintenance attachment could not be deleted", exception);
        }
    }

    private Path safeResolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Attachment storage key is required");
        }
        Path relative = Path.of(storageKey);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Absolute attachment paths are forbidden");
        }
        Path target = storageRoot.resolve(relative).normalize();
        assertInsideRoot(target);
        return target;
    }

    private void assertInsideRoot(Path candidate) {
        if (!candidate.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Attachment path escapes the configured root");
        }
    }
}
