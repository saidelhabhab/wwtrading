package ma.wwtrading.maintenance.api;

import java.time.Instant;
import java.util.UUID;

import ma.wwtrading.maintenance.domain.MaintenancePartRequestEntity;
import ma.wwtrading.maintenance.domain.MaintenancePartRequestStatus;

public record MaintenancePartRequestResponse(
    UUID id,
    UUID maintenanceId,
    String partName,
    String partReference,
    int quantity,
    String reason,
    MaintenancePartRequestStatus status,
    UUID requestedBy,
    Instant requestedAt,
    String reviewedBy,
    Instant reviewedAt
) {
    public static MaintenancePartRequestResponse from(MaintenancePartRequestEntity entity) {
        return new MaintenancePartRequestResponse(
            entity.getId(),
            entity.getMaintenanceId(),
            entity.getPartName(),
            entity.getPartReference(),
            entity.getQuantity(),
            entity.getReason(),
            entity.getStatus(),
            entity.getRequestedBy(),
            entity.getRequestedAt(),
            entity.getReviewedBy(),
            entity.getReviewedAt()
        );
    }
}

