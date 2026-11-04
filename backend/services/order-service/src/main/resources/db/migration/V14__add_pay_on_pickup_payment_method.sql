ALTER TABLE customer_order
    DROP CONSTRAINT IF EXISTS chk_customer_order_payment_method;

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_payment_method CHECK (
        payment_method IN ('CASH_ON_DELIVERY', 'BANK_TRANSFER', 'SPLIT_50_50', 'ONLINE_PAYMENT', 'PAY_ON_PICKUP')
    );

