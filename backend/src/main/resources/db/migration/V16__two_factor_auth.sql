-- Verificación en dos pasos (TOTP, RFC 6238). El secreto se guarda cifrado con AES-256-GCM
-- (APP_SECRETS_ENCRYPTION_KEY) y solo se activa tras confirmar un primer código.
ALTER TABLE users
    ADD COLUMN totp_secret_encrypted         TEXT,
    ADD COLUMN totp_pending_secret_encrypted TEXT,
    ADD COLUMN totp_enabled_at               TIMESTAMPTZ,
    -- Último intervalo de 30 s aceptado: impide reutilizar el mismo código.
    ADD COLUMN totp_last_step                BIGINT;

-- Códigos de recuperación de un solo uso (solo se guarda su SHA-256).
CREATE TABLE user_recovery_codes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    used_at    TIMESTAMPTZ
);

CREATE INDEX ix_user_recovery_codes_user ON user_recovery_codes (user_id) WHERE used_at IS NULL;

-- Paso intermedio del inicio de sesión: la contraseña ya se validó y falta el segundo factor.
CREATE TABLE login_challenges (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(64) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    consumed_at TIMESTAMPTZ,
    CONSTRAINT ck_login_challenges_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_login_challenges_expires ON login_challenges (expires_at);
