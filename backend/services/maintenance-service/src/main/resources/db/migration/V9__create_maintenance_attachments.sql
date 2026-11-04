ALTER TABLE maintenance
    ADD COLUMN client_reference VARCHAR(100);

CREATE TABLE maintenance_attachment (
    id UUID PRIMARY KEY,
    maintenance_id UUID NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    file_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_maintenance_attachment_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_maintenance_attachment_maintenance
        FOREIGN KEY (maintenance_id) REFERENCES maintenance(id) ON DELETE CASCADE,
    CONSTRAINT chk_maintenance_attachment_size CHECK (file_size > 0)
);

CREATE INDEX idx_maintenance_attachment_maintenance_created
    ON maintenance_attachment (maintenance_id, created_at);
