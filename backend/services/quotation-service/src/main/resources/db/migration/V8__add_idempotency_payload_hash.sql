ALTER TABLE quotation ADD COLUMN IF NOT EXISTS idempotency_payload_hash VARCHAR(64);

