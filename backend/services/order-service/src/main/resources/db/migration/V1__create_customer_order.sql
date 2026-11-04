CREATE TABLE customer_order (
    id UUID PRIMARY KEY,
    customer_reference VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_customer_order_status CHECK (
        status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')
    )
);

CREATE INDEX idx_customer_order_customer_reference ON customer_order (customer_reference);
CREATE INDEX idx_customer_order_status ON customer_order (status);
