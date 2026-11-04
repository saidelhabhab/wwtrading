package ma.wwtrading.support.infrastructure;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;

public interface SupportAttachmentStorage {
    String store(UUID conversationId, UUID messageId, String extension, InputStream content);
    Path resolve(String storageKey);
    void delete(String storageKey);
}
