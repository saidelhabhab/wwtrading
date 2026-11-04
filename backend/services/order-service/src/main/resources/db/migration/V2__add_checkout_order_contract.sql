ALTER TABLE customer_order
    ADD COLUMN shipping_first_name VARCHAR(100) NOT NULL,
    ADD COLUMN shipping_last_name VARCHAR(100) NOT NULL,
    ADD COLUMN shipping_company VARCHAR(150),
    ADD COLUMN shipping_phone VARCHAR(30) NOT NULL,
    ADD COLUMN shipping_address_line_1 VARCHAR(255) NOT NULL,
    ADD COLUMN shipping_address_line_2 VARCHAR(255),
    ADD COLUMN shipping_city VARCHAR(100) NOT NULL,
    ADD COLUMN shipping_postal_code VARCHAR(20) NOT NULL,
    ADD COLUMN shipping_country VARCHAR(100) NOT NULL,
    ADD COLUMN delivery_method VARCHAR(32) NOT NULL,
    ADD COLUMN payment_method VARCHAR(32) NOT NULL;

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_delivery_method CHECK (
        delivery_method IN ('DELIVERY')
    );

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_payment_method CHECK (
        payment_method IN ('CASH_ON_DELIVERY', 'BANK_TRANSFER', 'ONLINE_PAYMENT')
    );