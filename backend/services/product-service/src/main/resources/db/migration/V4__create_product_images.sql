CREATE TABLE product_image (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_product_image_product
        FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT uk_product_image_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_product_image_storage_key_not_blank CHECK (LENGTH(TRIM(storage_key)) > 0),
    CONSTRAINT chk_product_image_display_order_non_negative CHECK (display_order >= 0)
);

CREATE INDEX idx_product_image_product
    ON product_image (product_id);

CREATE INDEX idx_product_image_product_order
    ON product_image (product_id, display_order, created_at);

CREATE UNIQUE INDEX uk_product_image_one_main
    ON product_image (product_id)
    WHERE is_main = TRUE;
