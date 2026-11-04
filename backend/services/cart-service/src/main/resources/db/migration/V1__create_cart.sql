CREATE TABLE customer_cart (
    id UUID PRIMARY KEY,
    customer_reference VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_customer_cart_customer_reference UNIQUE (customer_reference)
);

CREATE TABLE cart_item (
    id UUID PRIMARY KEY,
    cart_id UUID NOT NULL,
    product_reference VARCHAR(64) NOT NULL,
    quantity INTEGER NOT NULL,
    CONSTRAINT fk_cart_item_cart FOREIGN KEY (cart_id)
        REFERENCES customer_cart (id) ON DELETE CASCADE,
    CONSTRAINT uk_cart_item_cart_product UNIQUE (cart_id, product_reference),
    CONSTRAINT chk_cart_item_quantity_positive CHECK (quantity > 0)
);

CREATE INDEX idx_cart_item_product_reference ON cart_item (product_reference);
