-- Fase 1: identidad, sesiones y estructura multiempresa.

CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name           VARCHAR(120) NOT NULL,
    email               VARCHAR(254) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    phone               VARCHAR(16)  NOT NULL,
    platform_role       VARCHAR(20),
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    terms_accepted_at   TIMESTAMPTZ,
    privacy_accepted_at TIMESTAMPTZ,
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lower CHECK (email = lower(email)),
    CONSTRAINT ck_users_phone CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_users_platform_role CHECK (platform_role IS NULL OR platform_role IN ('SUPER_ADMIN')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE businesses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id        UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    unique_code     VARCHAR(16)  NOT NULL,
    slug            VARCHAR(60)  NOT NULL,
    business_name   VARCHAR(120) NOT NULL,
    description     VARCHAR(1000),
    logo_url        VARCHAR(500),
    cover_url       VARCHAR(500),
    primary_color   VARCHAR(7),
    whatsapp_number VARCHAR(16),
    time_zone       VARCHAR(64)  NOT NULL DEFAULT 'America/Lima',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_businesses_slug UNIQUE (slug),
    CONSTRAINT uq_businesses_unique_code UNIQUE (unique_code),
    CONSTRAINT ck_businesses_slug CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND length(slug) BETWEEN 3 AND 60),
    CONSTRAINT ck_businesses_color CHECK (primary_color IS NULL OR primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_businesses_whatsapp CHECK (whatsapp_number IS NULL OR whatsapp_number ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_businesses_status CHECK (status IN ('ACTIVE', 'SUSPENDED'))
);

CREATE INDEX ix_businesses_owner ON businesses (owner_id);

CREATE TABLE business_members (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID        NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    role        VARCHAR(20) NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_members UNIQUE (business_id, user_id),
    CONSTRAINT ck_business_members_role CHECK (role IN ('OWNER', 'STAFF')),
    CONSTRAINT ck_business_members_status CHECK (status IN ('ACTIVE', 'REVOKED'))
);

CREATE INDEX ix_business_members_user ON business_members (user_id, status);

CREATE TABLE user_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash          VARCHAR(64) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    idle_expires_at     TIMESTAMPTZ NOT NULL,
    absolute_expires_at TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    ip_address          VARCHAR(45),
    user_agent          VARCHAR(255),
    CONSTRAINT uq_user_sessions_token UNIQUE (token_hash)
);

CREATE INDEX ix_user_sessions_user ON user_sessions (user_id) WHERE revoked_at IS NULL;
CREATE INDEX ix_user_sessions_expiry ON user_sessions (absolute_expires_at);

CREATE TABLE password_reset_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_reset_tokens UNIQUE (token_hash)
);

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id) WHERE used_at IS NULL;

-- Registro de auditoría: solo inserción.
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID REFERENCES users (id) ON DELETE SET NULL,
    business_id UUID REFERENCES businesses (id) ON DELETE SET NULL,
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(60),
    entity_id   UUID,
    metadata    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ix_audit_logs_business ON audit_logs (business_id, created_at DESC);
CREATE INDEX ix_audit_logs_actor ON audit_logs (actor_id, created_at DESC);
