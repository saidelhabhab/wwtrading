ALTER TABLE product
    ADD COLUMN available_quantity INTEGER;

ALTER TABLE product
    ADD CONSTRAINT chk_product_available_quantity_non_negative
        CHECK (available_quantity IS NULL OR available_quantity >= 0);