CREATE TABLE event_outbox (
    id UUID PRIMARY KEY,
    aggregate_id UUID NOT NULL,
    topic VARCHAR(160) NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    attempts INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_event_outbox_pending
    ON event_outbox (occurred_at)
    WHERE published_at IS NULL;
