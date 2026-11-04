CREATE TABLE customer_order_item (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES customer_order (id),
    product_reference VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    unit_price NUMERIC(19, 2) NOT NULL,
    subtotal NUMERIC(19, 2) NOT NULL,
    CONSTRAINT chk_customer_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT chk_customer_order_item_unit_price CHECK (unit_price >= 0),
    CONSTRAINT chk_customer_order_item_subtotal CHECK (subtotal >= 0)
);

ALTER TABLE customer_order
    ADD COLUMN order_total NUMERIC(19, 2) DEFAULT 0 NOT NULL;

CREATE INDEX idx_customer_order_item_order_id ON customer_order_item (order_id);