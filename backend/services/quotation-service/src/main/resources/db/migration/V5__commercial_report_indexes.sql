CREATE INDEX idx_prospect_created_at ON prospect (created_at);
CREATE INDEX idx_prospect_commercial_created_at
    ON prospect (assigned_commercial_subject, created_at);
