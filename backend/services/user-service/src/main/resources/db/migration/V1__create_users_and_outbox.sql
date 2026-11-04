CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    keycloak_subject VARCHAR(64) NOT NULL UNIQUE,
    email VARCHAR(320) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    locale VARCHAR(16),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_app_user_email ON app_user (LOWER(email));
