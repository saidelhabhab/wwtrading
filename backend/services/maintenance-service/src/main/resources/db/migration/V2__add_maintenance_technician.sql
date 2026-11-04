ALTER TABLE maintenance
    ADD COLUMN technician_id UUID;

CREATE INDEX idx_maintenance_technician_id ON maintenance (technician_id);
