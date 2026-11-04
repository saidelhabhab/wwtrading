-- Phase 2: dedicated stock system.
-- Stock is the single source of truth for product availability. The legacy
-- product.available_quantity column is migrated into stock.on_hand_quantity
-- (reserved starts at 0) and an initial IN movement preserves the audit trail.

CREATE TABLE stock (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    on_hand_quantity INTEGER NOT NULL,
    reserved_quantity INTEGER NOT NULL,
    low_stock_threshold INTEGER NOT NULL DEFAULT 5,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_stock_product UNIQUE (product_id),
    CONSTRAINT fk_stock_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT chk_stock_on_hand_non_negative CHECK (on_hand_quantity >= 0),
    CONSTRAINT chk_stock_reserved_non_negative CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_stock_reserved_lte_on_hand CHECK (reserved_quantity <= on_hand_quantity),
    CONSTRAINT chk_stock_threshold_non_negative CHECK (low_stock_threshold >= 0)
);

CREATE INDEX idx_stock_product ON stock (product_id);

CREATE TABLE stock_movement (
    id UUID PRIMARY KEY,
    stock_id UUID NOT NULL,
    product_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL,
    on_hand_delta INTEGER,
    reference VARCHAR(150),
    reason VARCHAR(255),
    actor VARCHAR(150) NOT NULL,
    idempotency_key VARCHAR(150),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_stock_movement_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_stock_movement_stock FOREIGN KEY (stock_id) REFERENCES stock (id) ON DELETE CASCADE,
    CONSTRAINT fk_stock_movement_product FOREIGN KEY (product_id) REFERENCES product (id) ON DELETE CASCADE,
    CONSTRAINT chk_stock_movement_type CHECK (type IN ('IN', 'OUT', 'ADJUST', 'RESERVE', 'RELEASE')),
    CONSTRAINT chk_stock_movement_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_stock_movement_actor_not_blank CHECK (LENGTH(TRIM(actor)) > 0)
);

CREATE INDEX idx_stock_movement_product ON stock_movement (product_id, created_at DESC);
CREATE INDEX idx_stock_movement_stock ON stock_movement (stock_id, created_at DESC);

-- Backfill: every product gets a stock row seeded from the legacy
-- available_quantity (NULL counts as 0). reserved starts at 0.
INSERT INTO stock (id, product_id, on_hand_quantity, reserved_quantity, low_stock_threshold, version, created_at, updated_at)
SELECT gen_random_uuid(),
       p.id,
       COALESCE(p.available_quantity, 0),
       0,
       5,
       0,
       p.created_at,
       p.updated_at
FROM product p;

-- Initial audit movement per product so the history is complete from day one.
INSERT INTO stock_movement (id, stock_id, product_id, type, quantity, on_hand_delta, reference, reason, actor, idempotency_key, created_at)
SELECT gen_random_uuid(),
       s.id,
       s.product_id,
       'IN',
       s.on_hand_quantity,
       s.on_hand_quantity,
       NULL,
       'Initial stock migrated from product.available_quantity',
       'system',
       'MIGRATION:V6:' || CAST(s.product_id AS TEXT),
       s.created_at
FROM stock s
WHERE s.on_hand_quantity > 0;
