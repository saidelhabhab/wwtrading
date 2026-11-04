package ma.wwtrading.maintenance.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "maintenance_history",
    indexes = {
        @Index(name = "idx_maintenance_history_maintenance_created_at", columnList = "maintenance_id, created_at DESC")
    }
)
public class MaintenanceHistoryEntity {

    public enum EventType {
        CREATED,
        STATUS_CHANGED,
        TECHNICIAN_ASSIGNED,
        TECHNICIAN_REASSIGNED,
        TECHNICIAN_UNASSIGNED
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "maintenance_id", nullable = false)
    private UUID maintenanceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private MaintenanceStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 32)
    private MaintenanceStatus toStatus;

    @Column(name = "technician_id")
    private UUID technicianId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected MaintenanceHistoryEntity() {
    }

    private MaintenanceHistoryEntity(UUID maintenanceId, EventType eventType, MaintenanceStatus fromStatus,
                                    MaintenanceStatus toStatus, UUID technicianId) {
        this.maintenanceId = maintenanceId;
        this.eventType = eventType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.technicianId = technicianId;
    }

    public static MaintenanceHistoryEntity created(UUID maintenanceId) {
        return new MaintenanceHistoryEntity(maintenanceId, EventType.CREATED, null, null, null);
    }

    public static MaintenanceHistoryEntity statusChanged(UUID maintenanceId, MaintenanceStatus fromStatus,
                                                       MaintenanceStatus toStatus) {
        return new MaintenanceHistoryEntity(maintenanceId, EventType.STATUS_CHANGED, fromStatus, toStatus, null);
    }

    public static MaintenanceHistoryEntity technicianAssigned(UUID maintenanceId, UUID technicianId) {
        return new MaintenanceHistoryEntity(maintenanceId, EventType.TECHNICIAN_ASSIGNED, null, null, technicianId);
    }

    public static MaintenanceHistoryEntity technicianReassigned(UUID maintenanceId, UUID technicianId) {
        return new MaintenanceHistoryEntity(maintenanceId, EventType.TECHNICIAN_REASSIGNED, null, null, technicianId);
    }

    public static MaintenanceHistoryEntity technicianUnassigned(UUID maintenanceId, UUID technicianId) {
        return new MaintenanceHistoryEntity(maintenanceId, EventType.TECHNICIAN_UNASSIGNED, null, null, technicianId);
    }

    @PrePersist
    void beforeInsert() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMaintenanceId() { return maintenanceId; }
    public EventType getEventType() { return eventType; }
    public MaintenanceStatus getFromStatus() { return fromStatus; }
    public MaintenanceStatus getToStatus() { return toStatus; }
    public UUID getTechnicianId() { return technicianId; }
    public Instant getCreatedAt() { return createdAt; }
}
