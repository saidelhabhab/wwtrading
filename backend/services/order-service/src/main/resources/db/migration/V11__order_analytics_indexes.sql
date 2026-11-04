CREATE INDEX idx_customer_order_created_at ON customer_order (created_at);
ALTER TABLE customer_order ADD COLUMN customer_subject VARCHAR(64);
CREATE INDEX idx_customer_order_customer_subject_created_at ON customer_order (customer_subject, created_at);
