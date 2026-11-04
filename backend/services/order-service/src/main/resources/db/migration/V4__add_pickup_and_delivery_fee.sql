ALTER TABLE customer_order
    ALTER COLUMN shipping_first_name DROP NOT NULL,
    ALTER COLUMN shipping_last_name DROP NOT NULL,
    ALTER COLUMN shipping_phone DROP NOT NULL,
    ALTER COLUMN shipping_address_line_1 DROP NOT NULL,
    ALTER COLUMN shipping_city DROP NOT NULL,
    ALTER COLUMN shipping_postal_code DROP NOT NULL,
    ALTER COLUMN shipping_country DROP NOT NULL,
    ADD COLUMN delivery_fee NUMERIC(19, 2) DEFAULT 0 NOT NULL,
    ADD CONSTRAINT chk_customer_order_delivery_fee CHECK (delivery_fee >= 0);

ALTER TABLE customer_order
    DROP CONSTRAINT chk_customer_order_delivery_method;

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_delivery_method CHECK (
        delivery_method IN ('DELIVERY', 'PICKUP')
    );