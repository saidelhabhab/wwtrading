CREATE TABLE user_address (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    company VARCHAR(200),
    phone VARCHAR(32),
    address_line_1 VARCHAR(255) NOT NULL,
    address_line_2 VARCHAR(255),
    city VARCHAR(120) NOT NULL,
    postal_code VARCHAR(32),
    country VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_user_address_user
        FOREIGN KEY (user_id) REFERENCES app_user (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_address_user_id ON user_address (user_id);

CREATE UNIQUE INDEX uk_user_address_one_default_per_user
    ON user_address (user_id)
    WHERE is_default = TRUE;
