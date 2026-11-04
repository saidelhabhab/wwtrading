CREATE TABLE support_conversation (
    id UUID PRIMARY KEY,
    client_keycloak_subject VARCHAR(64) NOT NULL,
    assigned_agent_keycloak_subject VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    closed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_support_conversation_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'CLOSED'))
);

CREATE TABLE support_message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sender_keycloak_subject VARCHAR(64) NOT NULL,
    sender_role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_support_message_conversation
        FOREIGN KEY (conversation_id) REFERENCES support_conversation(id) ON DELETE CASCADE,
    CONSTRAINT chk_support_sender_role CHECK (sender_role IN ('ADMIN', 'MANAGER', 'COMMERCIAL', 'CLIENT'))
);

CREATE TABLE support_attachment (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    uploader_keycloak_subject VARCHAR(64) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_support_attachment_message
        FOREIGN KEY (message_id) REFERENCES support_message(id) ON DELETE CASCADE,
    CONSTRAINT uk_support_attachment_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_support_attachment_size CHECK (file_size > 0)
);

CREATE INDEX idx_support_conversation_client ON support_conversation (client_keycloak_subject);
CREATE INDEX idx_support_conversation_agent ON support_conversation (assigned_agent_keycloak_subject);
CREATE INDEX idx_support_conversation_status ON support_conversation (status);
CREATE INDEX idx_support_message_conversation_created ON support_message (conversation_id, created_at);
CREATE INDEX idx_support_attachment_message ON support_attachment (message_id);
