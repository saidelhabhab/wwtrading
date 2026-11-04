package ma.wwtrading.support.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class LocalSupportAttachmentStorage implements SupportAttachmentStorage {
    private final Path storageRoot;

    public LocalSupportAttachmentStorage(
            @Value("${support.attachment.storage.path:${SUPPORT_ATTACHMENT_STORAGE_PATH:${java.io.tmpdir}/wwtrading/support-attachments}}")
            String storagePath) {
        storageRoot = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() {
        try {
            Files.createDirectories(storageRoot);
            if (!Files.isDirectory(storageRoot) || !Files.isWritable(storageRoot)) {
                throw new IllegalStateException("Support attachment storage is not writable");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Support attachment storage cannot be initialized", exception);
        }
    }

    @Override
    public String store(UUID conversationId, UUID messageId, String extension, InputStream content) {
        Path directory = safeResolve("conversations/" + conversationId + "/messages/" + messageId);
        try {
            Files.createDirectories(directory);
            Path target = directory.resolve(UUID.randomUUID() + "." + extension).normalize();
            assertInsideRoot(target);
            Files.copy(content, target);
            return storageRoot.relativize(target).toString().replace('\\', '/');
        } catch (IOException exception) {
            throw new IllegalStateException("Support attachment could not be stored", exception);
        }
    }

    @Override
    public Path resolve(String storageKey) {
        Path target = safeResolve(storageKey);
        try {
            Path realTarget = target.toRealPath();
            assertInsideRoot(realTarget);
            return realTarget;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Support attachment does not exist", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(safeResolve(storageKey));
        } catch (IOException exception) {
            throw new IllegalStateException("Support attachment could not be deleted", exception);
        }
    }

    private Path safeResolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) throw new IllegalArgumentException("Storage key is required");
        Path relative = Path.of(storageKey);
        if (relative.isAbsolute()) throw new IllegalArgumentException("Absolute attachment paths are forbidden");
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
