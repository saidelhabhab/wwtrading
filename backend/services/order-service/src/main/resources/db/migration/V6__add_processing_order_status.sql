ALTER TABLE customer_order
    DROP CONSTRAINT chk_customer_order_status;

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_status CHECK (
        status IN ('DRAFT', 'CONFIRMED', 'PROCESSING', 'CANCELLED')
    );
