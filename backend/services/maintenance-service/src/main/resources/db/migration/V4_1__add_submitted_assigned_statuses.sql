ALTER TABLE maintenance DROP CONSTRAINT chk_maintenance_status;
ALTER TABLE maintenance ADD CONSTRAINT chk_maintenance_status
    CHECK (status IN ('OPEN', 'SUBMITTED', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

ALTER TABLE maintenance ADD COLUMN assigned_at TIMESTAMP WITH TIME ZONE;

