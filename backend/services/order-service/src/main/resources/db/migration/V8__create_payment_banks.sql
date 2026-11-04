CREATE TABLE payment_bank (
    id UUID PRIMARY KEY,
    reference VARCHAR(100) NOT NULL UNIQUE,
    bank_name VARCHAR(150) NOT NULL,
    rib VARCHAR(50) NOT NULL,
    beneficiary VARCHAR(150) NOT NULL,
    image_url VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

ALTER TABLE customer_order
    ADD COLUMN payment_bank_id UUID,
    ADD COLUMN payment_bank_name VARCHAR(150),
    ADD COLUMN payment_bank_rib VARCHAR(50),
    ADD COLUMN payment_bank_beneficiary VARCHAR(150),
    ADD COLUMN payment_bank_logo_url VARCHAR(500);

