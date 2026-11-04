package ma.wwtrading.maintenance.api;

import java.time.Instant;
import java.util.UUID;

import ma.wwtrading.maintenance.domain.MaintenancePriority;
import ma.wwtrading.maintenance.domain.MaintenanceStatus;

public record MaintenanceResponse(
    UUID id,
    String customerReference,
    String clientReference,
    String productReference,
    String serialNumber,
    MaintenancePriority priority,
    UUID technicianId,
    String subject,
    String description,
    String diagnosis,
    String interventionNotes,
    String completionSummary,
    MaintenanceStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant assignedAt,
    Instant acceptedAt,
    Instant rejectedAt,
    Instant startedAt,
    Instant completedAt,
    Instant clientConfirmedAt
) {
    public MaintenanceResponse(
        UUID id,
        String customerReference,
        String productReference,
        String serialNumber,
        MaintenancePriority priority,
        UUID technicianId,
        String subject,
        String description,
        String diagnosis,
        String interventionNotes,
        String completionSummary,
        MaintenanceStatus status,
        Instant createdAt,
        Instant updatedAt,
        Instant assignedAt,
        Instant acceptedAt,
        Instant rejectedAt,
        Instant startedAt,
        Instant completedAt,
        Instant clientConfirmedAt
    ) {
        this(id, customerReference, null, productReference, serialNumber, priority, technicianId,
                subject, description, diagnosis, interventionNotes, completionSummary, status,
                createdAt, updatedAt, assignedAt, acceptedAt, rejectedAt, startedAt, completedAt,
                clientConfirmedAt);
    }

    public MaintenanceResponse(
        UUID id,
        String customerReference,
        String productReference,
        String subject,
        String description,
        MaintenanceStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, customerReference, null, productReference, null, MaintenancePriority.MEDIUM, null, subject, description, null, null, null, status, createdAt, updatedAt, null, null, null, null, null, null);
    }

    public MaintenanceResponse(
        UUID id,
        String customerReference,
        String productReference,
        String serialNumber,
        MaintenancePriority priority,
        UUID technicianId,
        String subject,
        String description,
        String diagnosis,
        MaintenanceStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
        this(id, customerReference, null, productReference, serialNumber, priority, technicianId, subject, description, diagnosis, null, null, status, createdAt, updatedAt, null, null, null, null, null, null);
    }
}
