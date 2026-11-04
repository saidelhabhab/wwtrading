ALTER TABLE product
    ADD COLUMN brand VARCHAR(100),
    ADD COLUMN model VARCHAR(150),
    ADD COLUMN machine_category VARCHAR(50),
    ADD COLUMN warranty_months INTEGER,
    ADD COLUMN warranty_copies INTEGER;

ALTER TABLE product
    ADD CONSTRAINT chk_product_machine_category
        CHECK (machine_category IS NULL OR machine_category IN (
            'GRAND_FORMAT_ECOSOLVENT',
            'DIGITAL_PRESS',
            'FINISHING_MACHINE'
        )),
    ADD CONSTRAINT chk_product_machine_category_type
        CHECK (machine_category IS NULL OR product_type = 'PRINTER'),
    ADD CONSTRAINT chk_product_warranty_months_non_negative
        CHECK (warranty_months IS NULL OR warranty_months >= 0),
    ADD CONSTRAINT chk_product_warranty_copies_non_negative
        CHECK (warranty_copies IS NULL OR warranty_copies >= 0);
