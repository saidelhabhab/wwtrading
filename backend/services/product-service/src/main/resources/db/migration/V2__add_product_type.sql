ALTER TABLE product
    ADD COLUMN product_type VARCHAR(32) NOT NULL DEFAULT 'PRINTER';

ALTER TABLE product
    ADD CONSTRAINT chk_product_type
        CHECK (product_type IN ('PRINTER', 'TONER', 'CONSUMABLE', 'SPARE_PART'));

ALTER TABLE product
    ALTER COLUMN product_type DROP DEFAULT;
