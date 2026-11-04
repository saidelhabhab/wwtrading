ALTER TABLE customer_order
    ADD COLUMN payment_status VARCHAR(32) DEFAULT 'UNPAID' NOT NULL;

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_payment_status CHECK (
        payment_status IN ('UNPAID', 'PAID')
    );