CREATE TABLE product (
    id UUID PRIMARY KEY,
    product_reference VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    price NUMERIC(19, 2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_product_reference UNIQUE (product_reference),
    CONSTRAINT chk_product_reference_not_blank CHECK (LENGTH(TRIM(product_reference)) > 0),
    CONSTRAINT chk_product_name_not_blank CHECK (LENGTH(TRIM(name)) > 0),
    CONSTRAINT chk_product_price_non_negative CHECK (price >= 0)
);

CREATE INDEX idx_product_active ON product (active);
