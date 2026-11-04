ALTER TABLE maintenance
    ADD COLUMN priority VARCHAR(32) DEFAULT 'MEDIUM' NOT NULL,
    ADD COLUMN serial_number VARCHAR(120),
    ADD COLUMN intervention_notes TEXT;

ALTER TABLE maintenance
    ADD CONSTRAINT chk_maintenance_priority
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL'));

CREATE INDEX idx_maintenance_priority ON maintenance (priority);

