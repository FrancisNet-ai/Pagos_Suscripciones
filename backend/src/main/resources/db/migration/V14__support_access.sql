-- Acceso de soporte a datos de compradores: solo con autorización temporal del titular. Cada consulta
-- del superadministrador exige un motivo y queda en la auditoría del negocio, visible para el titular.
CREATE TABLE support_grants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID         NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    granted_by  UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    note        VARCHAR(300),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    revoked_by  UUID REFERENCES users (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_support_grants_expiry CHECK (expires_at > created_at)
);

CREATE INDEX ix_support_grants_business ON support_grants (business_id, created_at DESC);
