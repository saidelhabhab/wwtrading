package ma.wwtrading.maintenance.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "maintenance_attachment")
public class MaintenanceAttachmentEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "maintenance_id", nullable = false)
    private UUID maintenanceId;

    @Column(name = "storage_key", nullable = false, unique = true, length = 512)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MaintenanceAttachmentEntity() {
    }

    public MaintenanceAttachmentEntity(UUID maintenanceId, String storageKey, String originalFilename,
                                       String contentType, long fileSize) {
        this.maintenanceId = maintenanceId;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
    }

    @PrePersist
    void beforeInsert() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMaintenanceId() { return maintenanceId; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public Instant getCreatedAt() { return createdAt; }
}
