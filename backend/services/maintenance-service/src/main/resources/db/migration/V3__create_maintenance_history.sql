CREATE TABLE maintenance_history (
    id UUID PRIMARY KEY,
    maintenance_id UUID NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32),
    technician_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_maintenance_history_maintenance
        FOREIGN KEY (maintenance_id) REFERENCES maintenance (id) ON DELETE CASCADE,
    CONSTRAINT chk_maintenance_history_event_type
        CHECK (event_type IN ('CREATED', 'STATUS_CHANGED', 'TECHNICIAN_ASSIGNED', 'TECHNICIAN_REASSIGNED', 'TECHNICIAN_UNASSIGNED')),
    CONSTRAINT chk_maintenance_history_status_transition
        CHECK (
            (event_type <> 'STATUS_CHANGED') OR (from_status IS NOT NULL AND to_status IS NOT NULL)
        )
);

CREATE INDEX idx_maintenance_history_maintenance_created_at
    ON maintenance_history (maintenance_id, created_at DESC);

CREATE INDEX idx_maintenance_history_event_type
    ON maintenance_history (event_type);
