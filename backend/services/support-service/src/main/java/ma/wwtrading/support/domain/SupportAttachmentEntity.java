package ma.wwtrading.support.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "support_attachment")
public class SupportAttachmentEntity {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "uploader_keycloak_subject", nullable = false, length = 64)
    private String uploaderKeycloakSubject;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
    private String storageKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SupportAttachmentEntity() {}

    public SupportAttachmentEntity(UUID messageId, String uploaderKeycloakSubject, String originalFilename,
                                   String contentType, long fileSize, String storageKey) {
        this.messageId = messageId;
        this.uploaderKeycloakSubject = uploaderKeycloakSubject;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.storageKey = storageKey;
    }

    @PrePersist
    void beforeInsert() { createdAt = Instant.now(); }

    public UUID getId() { return id; }
    public UUID getMessageId() { return messageId; }
    public String getUploaderKeycloakSubject() { return uploaderKeycloakSubject; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getStorageKey() { return storageKey; }
    public Instant getCreatedAt() { return createdAt; }
}
