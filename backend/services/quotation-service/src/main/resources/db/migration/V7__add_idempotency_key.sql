ALTER TABLE quotation ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS idx_quotation_idempotency_key ON quotation (idempotency_key) WHERE idempotency_key IS NOT NULL;

