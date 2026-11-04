CREATE TABLE maintenance (
    id UUID PRIMARY KEY,
    customer_reference VARCHAR(64) NOT NULL,
    product_reference VARCHAR(64),
    subject VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_maintenance_customer_reference_not_blank
        CHECK (LENGTH(TRIM(customer_reference)) > 0),
    CONSTRAINT chk_maintenance_product_reference_not_blank
        CHECK (product_reference IS NULL OR LENGTH(TRIM(product_reference)) > 0),
    CONSTRAINT chk_maintenance_subject_not_blank
        CHECK (LENGTH(TRIM(subject)) > 0),
    CONSTRAINT chk_maintenance_description_not_blank
        CHECK (LENGTH(TRIM(description)) > 0),
    CONSTRAINT chk_maintenance_status
        CHECK (status IN ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'))
);

CREATE INDEX idx_maintenance_customer_reference ON maintenance (customer_reference);
CREATE INDEX idx_maintenance_product_reference ON maintenance (product_reference);
CREATE INDEX idx_maintenance_status ON maintenance (status);
