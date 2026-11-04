CREATE TABLE quotation_line (
    id UUID PRIMARY KEY,
    quotation_id UUID NOT NULL REFERENCES quotation(id) ON DELETE CASCADE,
    product_id UUID NOT NULL,
    product_reference VARCHAR(64) NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    catalog_unit_price NUMERIC(19, 2) NOT NULL CHECK (catalog_unit_price >= 0),
    requested_quantity INTEGER NOT NULL CHECK (requested_quantity > 0),
    proposed_quantity INTEGER CHECK (proposed_quantity > 0),
    proposed_unit_price NUMERIC(19, 2) CHECK (proposed_unit_price >= 0),
    CONSTRAINT uk_quotation_line_product UNIQUE (quotation_id, product_id)
);

CREATE INDEX idx_quotation_line_quotation ON quotation_line (quotation_id);

ALTER TABLE quotation ADD COLUMN discount NUMERIC(19, 2) CHECK (discount >= 0);
ALTER TABLE quotation ADD COLUMN delivery_fee NUMERIC(19, 2) CHECK (delivery_fee >= 0);
ALTER TABLE quotation ADD COLUMN valid_until DATE;
