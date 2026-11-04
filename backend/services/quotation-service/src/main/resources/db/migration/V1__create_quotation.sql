CREATE TABLE quotation (
    id UUID PRIMARY KEY,
    customer_reference VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    request_description VARCHAR(4000) NOT NULL,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_quotation_status CHECK (
        status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED')
    )
);

CREATE INDEX idx_quotation_customer_reference ON quotation (customer_reference);
CREATE INDEX idx_quotation_status ON quotation (status);
