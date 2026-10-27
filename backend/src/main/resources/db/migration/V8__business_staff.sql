-- Colaboradores (BUSINESS_STAFF): permisos por sección e invitaciones de un solo uso.

-- Secciones habilitadas para un colaborador, separadas por coma (PRODUCTS, ORDERS, CUSTOMERS, RENEWALS, REPORTS).
-- El titular (OWNER) tiene acceso completo y no usa esta columna.
ALTER TABLE business_members
    ADD COLUMN permissions VARCHAR(200) NOT NULL DEFAULT '',
    ADD COLUMN revoked_at  TIMESTAMPTZ,
    ADD CONSTRAINT ck_business_members_permissions CHECK (permissions ~ '^([A-Z_]+(,[A-Z_]+)*)?$');

-- Un negocio tiene exactamente un titular activo.
CREATE UNIQUE INDEX uq_business_members_owner ON business_members (business_id) WHERE role = 'OWNER' AND status = 'ACTIVE';
-- MVP: una cuenta pertenece a un solo negocio.
CREATE UNIQUE INDEX uq_business_members_user ON business_members (user_id);

CREATE TABLE staff_invitations (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id      UUID         NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    email            VARCHAR(254) NOT NULL,
    full_name        VARCHAR(120) NOT NULL,
    permissions      VARCHAR(200) NOT NULL,
    -- Solo se guarda el SHA-256 del token; el enlace se muestra una vez al titular.
    token_hash       VARCHAR(64)  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    status           VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    invited_by       UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    accepted_user_id UUID REFERENCES users (id) ON DELETE RESTRICT,
    accepted_at      TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_invitations_token UNIQUE (token_hash),
    CONSTRAINT ck_staff_invitations_email CHECK (email = lower(email)),
    CONSTRAINT ck_staff_invitations_status CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED')),
    CONSTRAINT ck_staff_invitations_permissions CHECK (permissions ~ '^[A-Z_]+(,[A-Z_]+)*$')
);

-- Una invitación pendiente por correo y negocio.
CREATE UNIQUE INDEX uq_staff_invitations_pending ON staff_invitations (business_id, email) WHERE status = 'PENDING';
CREATE INDEX ix_staff_invitations_business ON staff_invitations (business_id, created_at DESC);
