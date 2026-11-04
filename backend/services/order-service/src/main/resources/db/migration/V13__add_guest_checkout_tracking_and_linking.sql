ALTER TABLE customer_order
    ALTER COLUMN customer_reference TYPE VARCHAR(320),
    ADD COLUMN order_number VARCHAR(32),
    ADD COLUMN guest_order BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN contact_full_name VARCHAR(200),
    ADD COLUMN contact_email VARCHAR(320),
    ADD COLUMN contact_phone VARCHAR(32),
    ADD COLUMN tracking_token_hash VARCHAR(64),
    ADD COLUMN tracking_token_expires_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN tracking_token_revoked BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN idempotency_key VARCHAR(128),
    ADD COLUMN linked_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE customer_order_item
    ADD COLUMN product_name VARCHAR(255);

UPDATE customer_order
SET order_number = 'WWT-' || UPPER(SUBSTRING(REPLACE(id::text, '-', ''), 1, 8))
WHERE order_number IS NULL;

UPDATE customer_order
SET contact_email = customer_reference
WHERE contact_email IS NULL AND customer_reference IS NOT NULL;

CREATE UNIQUE INDEX idx_customer_order_order_number
    ON customer_order (order_number)
    WHERE order_number IS NOT NULL;

CREATE UNIQUE INDEX idx_customer_order_tracking_token_hash
    ON customer_order (tracking_token_hash)
    WHERE tracking_token_hash IS NOT NULL;

CREATE UNIQUE INDEX idx_customer_order_idempotency_key
    ON customer_order (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_customer_order_guest_email
    ON customer_order (LOWER(customer_reference), guest_order)
    WHERE customer_subject IS NULL;

