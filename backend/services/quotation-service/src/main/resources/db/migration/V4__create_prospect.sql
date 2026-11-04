CREATE TABLE prospect (
    id UUID PRIMARY KEY,
    company_name VARCHAR(200) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    phone VARCHAR(30),
    email VARCHAR(160),
    city VARCHAR(100),
    activity VARCHAR(160),
    source VARCHAR(80),
    status VARCHAR(32) NOT NULL,
    notes TEXT,
    assigned_commercial_subject VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_prospect_status CHECK (
        status IN ('NEW', 'CONTACTED', 'INTERESTED', 'QUOTE_SENT', 'WON', 'LOST')
    )
);

CREATE INDEX idx_prospect_assigned_commercial ON prospect (assigned_commercial_subject);
CREATE INDEX idx_prospect_status ON prospect (status);
CREATE INDEX idx_prospect_updated_at ON prospect (updated_at);
