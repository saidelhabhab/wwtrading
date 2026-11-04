ALTER TABLE notification
    ADD COLUMN recipient_phone VARCHAR(32),
    DROP CONSTRAINT IF EXISTS ck_notification_channel,
    ADD CONSTRAINT ck_notification_channel CHECK (
        channel IN ('IN_APP', 'EMAIL', 'WHATSAPP', 'BOTH', 'ALL')
    );

CREATE INDEX IF NOT EXISTS idx_notification_entity_type_id
    ON notification (entity_type, entity_id, created_at DESC);

CREATE TABLE notification_whatsapp_delivery (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(128),
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_whatsapp_delivery_notification UNIQUE (notification_id),
    CONSTRAINT fk_notification_whatsapp_delivery_notification
        FOREIGN KEY (notification_id) REFERENCES notification(id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_whatsapp_delivery_status
        CHECK (status IN ('DISABLED', 'PENDING', 'SENT', 'FAILED', 'RETRYING', 'DEAD_LETTER')),
    CONSTRAINT ck_notification_whatsapp_delivery_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notification_whatsapp_delivery_due
    ON notification_whatsapp_delivery (status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED', 'RETRYING');

