ALTER TABLE quotation ADD COLUMN customer_subject VARCHAR(64);
CREATE INDEX idx_quotation_created_at ON quotation (created_at);
CREATE INDEX idx_quotation_customer_subject_created_at ON quotation (customer_subject, created_at);
