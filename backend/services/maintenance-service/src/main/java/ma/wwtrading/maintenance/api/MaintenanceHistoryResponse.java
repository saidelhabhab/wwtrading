package ma.wwtrading.maintenance.api;

import java.time.Instant;
import java.util.UUID;

import ma.wwtrading.maintenance.domain.MaintenanceHistoryEntity;
import ma.wwtrading.maintenance.domain.MaintenanceStatus;

public record MaintenanceHistoryResponse(
    UUID id,
    MaintenanceHistoryEntity.EventType eventType,
    MaintenanceStatus fromStatus,
    MaintenanceStatus toStatus,
    UUID technicianId,
    Instant createdAt
) {
    public static MaintenanceHistoryResponse from(MaintenanceHistoryEntity entity) {
        return new MaintenanceHistoryResponse(
            entity.getId(),
            entity.getEventType(),
            entity.getFromStatus(),
            entity.getToStatus(),
            entity.getTechnicianId(),
            entity.getCreatedAt()
        );
    }
}
