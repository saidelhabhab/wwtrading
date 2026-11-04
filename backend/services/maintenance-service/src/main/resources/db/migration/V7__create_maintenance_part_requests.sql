ALTER TABLE maintenance DROP CONSTRAINT IF EXISTS chk_maintenance_status;
ALTER TABLE maintenance ADD CONSTRAINT chk_maintenance_status
    CHECK (status IN ('OPEN', 'SUBMITTED', 'ASSIGNED', 'ACCEPTED', 'REJECTED', 'IN_PROGRESS', 'WAITING_FOR_PART', 'COMPLETED', 'CANCELLED'));

CREATE TABLE IF NOT EXISTS maintenance_part_requests (
    id UUID PRIMARY KEY,
    maintenance_id UUID NOT NULL REFERENCES maintenance(id) ON DELETE CASCADE,
    part_name VARCHAR(200) NOT NULL,
    part_reference VARCHAR(100),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    reason TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'REQUESTED',
    requested_by UUID NOT NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reviewed_by VARCHAR(120),
    reviewed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_part_request_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_part_requests_maintenance_id ON maintenance_part_requests(maintenance_id);

