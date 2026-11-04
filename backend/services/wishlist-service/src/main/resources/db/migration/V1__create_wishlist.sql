CREATE TABLE customer_wishlist (
    id UUID PRIMARY KEY,
    customer_reference VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_customer_wishlist_customer_reference UNIQUE (customer_reference),
    CONSTRAINT chk_customer_wishlist_customer_reference_not_blank
        CHECK (LENGTH(TRIM(customer_reference)) > 0)
);

CREATE TABLE wishlist_item (
    id UUID PRIMARY KEY,
    wishlist_id UUID NOT NULL,
    product_reference VARCHAR(64) NOT NULL,
    CONSTRAINT fk_wishlist_item_wishlist FOREIGN KEY (wishlist_id)
        REFERENCES customer_wishlist (id) ON DELETE CASCADE,
    CONSTRAINT uk_wishlist_item_wishlist_product UNIQUE (wishlist_id, product_reference),
    CONSTRAINT chk_wishlist_item_product_reference_not_blank
        CHECK (LENGTH(TRIM(product_reference)) > 0)
);

CREATE INDEX idx_wishlist_item_product_reference ON wishlist_item (product_reference);
