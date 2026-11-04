package ma.wwtrading.maintenance.api;

import java.time.Instant;
import java.util.UUID;

public record MaintenanceAttachmentResponse(
        UUID id,
        String filename,
        String contentType,
        long size,
        String contentUrl,
        Instant createdAt) {
}
