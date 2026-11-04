CREATE TABLE notification (
    id UUID PRIMARY KEY,
    recipient_subject VARCHAR(128) NOT NULL,
    recipient_email VARCHAR(320),
    type VARCHAR(64) NOT NULL,
    channel VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    message TEXT NOT NULL,
    action_url VARCHAR(1000),
    entity_type VARCHAR(100),
    entity_id VARCHAR(255),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ,
    metadata JSONB,
    CONSTRAINT ck_notification_type CHECK (type IN (
        'WELCOME', 'LOGIN', 'ANNOUNCEMENT', 'PRODUCT_CREATED', 'ORDER_CREATED',
        'ORDER_STATUS_CHANGED', 'PAYMENT_STATUS_CHANGED', 'QUOTATION_CREATED',
        'QUOTATION_STATUS_CHANGED', 'MAINTENANCE_CREATED', 'MAINTENANCE_ASSIGNED',
        'MAINTENANCE_STATUS_CHANGED', 'MAINTENANCE_COMPLETED', 'SUPPORT_MESSAGE',
        'SUPPORT_REPLY', 'SYSTEM'
    )),
    CONSTRAINT ck_notification_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'BOTH'))
);

CREATE INDEX idx_notification_recipient_created
    ON notification (recipient_subject, created_at DESC);

CREATE INDEX idx_notification_recipient_unread
    ON notification (recipient_subject)
    WHERE read_at IS NULL;
