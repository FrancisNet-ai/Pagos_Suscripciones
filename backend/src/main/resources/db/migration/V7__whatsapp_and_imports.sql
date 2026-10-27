-- Fase 7: integración oficial con WhatsApp Business Platform (Cloud API) e importación masiva de clientes.

-- Conexión de cada negocio con su número de WhatsApp Business. El token se guarda cifrado (AES-GCM);
-- nunca se devuelve a ningún cliente.
CREATE TABLE whatsapp_integrations (
    business_id            UUID PRIMARY KEY REFERENCES businesses (id) ON DELETE RESTRICT,
    phone_number_id        VARCHAR(40)  NOT NULL,
    waba_id                VARCHAR(40),
    display_phone          VARCHAR(40),
    access_token_encrypted TEXT         NOT NULL,
    reminder_template      VARCHAR(512) NOT NULL,
    farewell_template      VARCHAR(512),
    template_language      VARCHAR(10)  NOT NULL DEFAULT 'es',
    enabled                BOOLEAN      NOT NULL DEFAULT FALSE,
    status                 VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    last_verified_at       TIMESTAMPTZ,
    last_error             VARCHAR(500),
    version                BIGINT       NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Un número solo puede pertenecer a un negocio: así el webhook se asocia sin ambigüedad.
    CONSTRAINT uq_whatsapp_integrations_phone UNIQUE (phone_number_id),
    CONSTRAINT ck_whatsapp_integrations_status CHECK (status IN ('UNVERIFIED', 'VERIFIED', 'ERROR')),
    CONSTRAINT ck_whatsapp_integrations_enabled CHECK (NOT enabled OR status = 'VERIFIED')
);

-- Eventos de webhook ya procesados (idempotencia: Meta reintenta entregas).
CREATE TABLE whatsapp_webhook_events (
    event_key   VARCHAR(200) PRIMARY KEY,
    business_id UUID REFERENCES businesses (id) ON DELETE RESTRICT,
    kind        VARCHAR(20) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE renewal_notifications
    ADD COLUMN attempts       INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN failure_reason VARCHAR(500),
    ADD COLUMN delivered_at   TIMESTAMPTZ,
    ADD COLUMN read_at        TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_renewal_notifications_provider ON renewal_notifications (provider_message_id) WHERE provider_message_id IS NOT NULL;
CREATE INDEX ix_renewal_notifications_dispatch ON renewal_notifications (scheduled_at) WHERE delivery_status = 'PENDING';

-- Importación de clientes: el archivo se analiza una vez y se guarda la vista previa; al confirmar
-- se usan exactamente esas filas.
CREATE TABLE customer_imports (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id  UUID         NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    file_name    VARCHAR(255) NOT NULL,
    status       VARCHAR(15)  NOT NULL DEFAULT 'PREVIEW',
    rows         JSONB        NOT NULL,
    total_rows   INTEGER      NOT NULL,
    valid_rows   INTEGER      NOT NULL,
    result       JSONB,
    created_by   UUID         NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    expires_at   TIMESTAMPTZ  NOT NULL,
    committed_at TIMESTAMPTZ,
    version      BIGINT       NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_customer_imports_status CHECK (status IN ('PREVIEW', 'COMMITTED', 'DISCARDED'))
);

CREATE INDEX ix_customer_imports_business ON customer_imports (business_id, created_at DESC);

