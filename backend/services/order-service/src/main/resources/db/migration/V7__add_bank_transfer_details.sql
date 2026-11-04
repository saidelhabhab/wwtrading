ALTER TABLE customer_order
    ADD COLUMN selected_bank_reference VARCHAR(100),
    ADD COLUMN transfer_proof_reference VARCHAR(255);