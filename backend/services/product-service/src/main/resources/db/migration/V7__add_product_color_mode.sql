ALTER TABLE product
    ADD COLUMN color_mode VARCHAR(16);

ALTER TABLE product
    ADD CONSTRAINT chk_product_color_mode
        CHECK (color_mode IS NULL OR color_mode IN ('COLOR', 'MONOCHROME')),
    ADD CONSTRAINT chk_product_color_mode_type
        CHECK (color_mode IS NULL OR product_type = 'PRINTER');

CREATE INDEX idx_product_color_mode ON product (color_mode);