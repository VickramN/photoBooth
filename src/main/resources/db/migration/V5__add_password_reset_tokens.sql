CREATE TABLE password_reset_token (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_password_reset_token_user FOREIGN KEY (user_id) REFERENCES app_user (id)
);

CREATE INDEX idx_password_reset_token_user_id ON password_reset_token (user_id);