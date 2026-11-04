package ma.wwtrading.maintenance.domain;

public enum MaintenanceStatus {
    OPEN,
    SUBMITTED,
    ASSIGNED,
    ACCEPTED,
    REJECTED,
    IN_PROGRESS,
    WAITING_FOR_PART,
    COMPLETION_PENDING_CONFIRMATION,
    COMPLETED,
    CANCELLED
}
