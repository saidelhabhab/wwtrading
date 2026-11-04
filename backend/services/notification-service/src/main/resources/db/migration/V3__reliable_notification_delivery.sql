ALTER TABLE notification ADD COLUMN source_event_id UUID;

CREATE UNIQUE INDEX uk_notification_event_recipient_type
    ON notification (source_event_id, recipient_subject, type)
    WHERE source_event_id IS NOT NULL;

CREATE TABLE notification_email_delivery (
    id UUID PRIMARY KEY,
    notification_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_attempt_at TIMESTAMPTZ,
    sent_at TIMESTAMPTZ,
    last_error VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_notification_email_delivery_notification UNIQUE (notification_id),
    CONSTRAINT fk_notification_email_delivery_notification
        FOREIGN KEY (notification_id) REFERENCES notification(id) ON DELETE CASCADE,
    CONSTRAINT ck_notification_email_delivery_status
        CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'RETRYING', 'DEAD_LETTER')),
    CONSTRAINT ck_notification_email_delivery_attempts CHECK (attempt_count >= 0)
);

CREATE INDEX idx_notification_email_delivery_due
    ON notification_email_delivery (status, next_attempt_at)
    WHERE status IN ('PENDING', 'FAILED', 'RETRYING');
