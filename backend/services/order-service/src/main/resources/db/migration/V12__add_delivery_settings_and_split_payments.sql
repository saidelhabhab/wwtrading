CREATE TABLE delivery_settings (
    id VARCHAR(32) PRIMARY KEY,
    fees_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    kenitra_area_fee NUMERIC(19, 2) NOT NULL DEFAULT 300.00,
    casablanca_fee NUMERIC(19, 2) NOT NULL DEFAULT 300.00,
    other_areas_fee NUMERIC(19, 2) NOT NULL DEFAULT 400.00,
    free_delivery_threshold NUMERIC(19, 2) NOT NULL DEFAULT 20000.00,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_delivery_settings_kenitra_fee CHECK (kenitra_area_fee >= 0),
    CONSTRAINT chk_delivery_settings_casablanca_fee CHECK (casablanca_fee >= 0),
    CONSTRAINT chk_delivery_settings_other_fee CHECK (other_areas_fee >= 0),
    CONSTRAINT chk_delivery_settings_free_threshold CHECK (free_delivery_threshold >= 0)
);

INSERT INTO delivery_settings (
    id,
    fees_enabled,
    kenitra_area_fee,
    casablanca_fee,
    other_areas_fee,
    free_delivery_threshold,
    updated_at
) VALUES (
    'DEFAULT',
    TRUE,
    300.00,
    300.00,
    400.00,
    20000.00,
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;

ALTER TABLE customer_order
    ALTER COLUMN shipping_company TYPE VARCHAR(200),
    ALTER COLUMN shipping_phone TYPE VARCHAR(32),
    ALTER COLUMN shipping_city TYPE VARCHAR(120),
    ALTER COLUMN shipping_postal_code TYPE VARCHAR(32),
    ADD COLUMN payment_plan VARCHAR(32) NOT NULL DEFAULT 'CASH_ON_DELIVERY',
    ADD COLUMN amount_paid NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN upfront_payment_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN remaining_delivery_amount NUMERIC(19, 2) NOT NULL DEFAULT 0.00,
    ADD COLUMN first_installment_confirmed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN second_installment_confirmed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN delivery_fee_waived_reason VARCHAR(32),
    ADD COLUMN delivery_area VARCHAR(32);

UPDATE customer_order
SET payment_plan = CASE
        WHEN payment_method = 'BANK_TRANSFER' THEN 'FULL_BANK_TRANSFER'
        WHEN delivery_method = 'PICKUP' THEN 'PAY_ON_PICKUP'
        ELSE 'CASH_ON_DELIVERY'
    END,
    upfront_payment_amount = CASE
        WHEN payment_method = 'BANK_TRANSFER' THEN order_total
        ELSE 0.00
    END,
    remaining_delivery_amount = CASE
        WHEN payment_method = 'BANK_TRANSFER' THEN 0.00
        ELSE order_total
    END,
    amount_paid = CASE
        WHEN payment_status = 'PAID' THEN order_total
        ELSE 0.00
    END,
    first_installment_confirmed_at = CASE
        WHEN payment_status = 'PAID' THEN updated_at
        ELSE NULL
    END,
    paid_at = CASE
        WHEN payment_status = 'PAID' THEN updated_at
        ELSE NULL
    END,
    delivery_area = CASE
        WHEN delivery_method = 'PICKUP' THEN 'PICKUP'
        ELSE NULL
    END,
    delivery_fee_waived_reason = CASE
        WHEN delivery_method = 'PICKUP' THEN 'PICKUP'
        ELSE NULL
    END;

ALTER TABLE customer_order
    DROP CONSTRAINT IF EXISTS chk_customer_order_payment_method;

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_payment_method CHECK (
        payment_method IN ('CASH_ON_DELIVERY', 'BANK_TRANSFER', 'SPLIT_50_50', 'ONLINE_PAYMENT')
    );

ALTER TABLE customer_order
    DROP CONSTRAINT IF EXISTS chk_customer_order_payment_status;

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_payment_status CHECK (
        payment_status IN ('UNPAID', 'PARTIALLY_PAID', 'PAID')
    );

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_payment_plan CHECK (
        payment_plan IN ('FULL_BANK_TRANSFER', 'SPLIT_50_50', 'CASH_ON_DELIVERY', 'PAY_ON_PICKUP')
    );

ALTER TABLE customer_order
    ADD CONSTRAINT chk_customer_order_amount_paid CHECK (
        amount_paid >= 0 AND amount_paid <= order_total
    );

