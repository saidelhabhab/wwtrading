ALTER TABLE customer_order
    ADD COLUMN second_installment_payment_method VARCHAR(32),
    ADD COLUMN second_installment_confirmed_by VARCHAR(100),
    ADD COLUMN first_transfer_proof_submitted_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN second_transfer_proof_submitted_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE customer_order
    ADD CONSTRAINT chk_second_installment_payment_method CHECK (
        second_installment_payment_method IS NULL OR second_installment_payment_method IN ('BANK_TRANSFER', 'CASH')
    );

UPDATE customer_order
SET second_installment_payment_method = 'BANK_TRANSFER'
WHERE second_transfer_proof_reference IS NOT NULL AND second_installment_payment_method IS NULL;

UPDATE customer_order
SET second_installment_confirmed_by = 'ADMIN'
WHERE second_installment_confirmed_at IS NOT NULL AND second_installment_confirmed_by IS NULL;

