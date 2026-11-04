ALTER TABLE maintenance ADD COLUMN customer_subject VARCHAR(64);
CREATE INDEX idx_maintenance_created_at ON maintenance (created_at);
CREATE INDEX idx_maintenance_customer_subject_created_at ON maintenance (customer_subject, created_at);
CREATE INDEX idx_maintenance_technician_created_at ON maintenance (technician_id, created_at);
