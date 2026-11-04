ALTER TABLE customer_order
    ADD COLUMN quotation_id UUID,
    ADD COLUMN discount NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_customer_order_discount CHECK (discount >= 0),
    ADD CONSTRAINT uk_customer_order_quotation UNIQUE (quotation_id);
