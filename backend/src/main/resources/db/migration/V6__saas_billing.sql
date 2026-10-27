-- Fase 6: comercialización del SaaS. Planes, contrato de cada negocio, cobros SaaS a vendedores,
-- configuración general de la plataforma y suspensión de negocios.
-- Estas tablas NO comparten datos con las ventas de los vendedores (orders / customer_payments).

ALTER TABLE businesses
    ADD COLUMN suspension_reason VARCHAR(500),
    ADD COLUMN suspended_at      TIMESTAMPTZ,
    ADD CONSTRAINT ck_businesses_suspension CHECK (status <> 'SUSPENDED' OR suspended_at IS NOT NULL);

CREATE TABLE saas_plans (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name              VARCHAR(80)    NOT NULL,
    description       VARCHAR(500),
    price             NUMERIC(12, 2) NOT NULL,
    billing_period    VARCHAR(10)    NOT NULL,
    -- NULL = sin límite.
    product_limit     INTEGER,
    customer_limit    INTEGER,
    admin_user_limit  INTEGER        NOT NULL DEFAULT 1,
    feature_reports   BOOLEAN        NOT NULL DEFAULT TRUE,
    feature_reminders BOOLEAN        NOT NULL DEFAULT TRUE,
    feature_branding  BOOLEAN        NOT NULL DEFAULT TRUE,
    is_active         BOOLEAN        NOT NULL DEFAULT TRUE,
    sort_order        INTEGER        NOT NULL DEFAULT 0,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_saas_plans_name UNIQUE (name),
    CONSTRAINT ck_saas_plans_price CHECK (price >= 0),
    CONSTRAINT ck_saas_plans_period CHECK (billing_period IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT ck_saas_plans_product_limit CHECK (product_limit IS NULL OR product_limit > 0),
    CONSTRAINT ck_saas_plans_customer_limit CHECK (customer_limit IS NULL OR customer_limit > 0),
    CONSTRAINT ck_saas_plans_admin_limit CHECK (admin_user_limit >= 1)
);

-- Contrato del negocio con la plataforma: una fila por negocio con el plan y la vigencia actuales.
-- La suspensión administrativa se guarda en businesses.status para no alterar las fechas pagadas.
CREATE TABLE saas_subscriptions (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id  UUID        NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    plan_id      UUID        NOT NULL REFERENCES saas_plans (id) ON DELETE RESTRICT,
    status       VARCHAR(20) NOT NULL,
    start_at     TIMESTAMPTZ,
    expires_at   TIMESTAMPTZ,
    grace_until  TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_saas_subscriptions_business UNIQUE (business_id),
    CONSTRAINT ck_saas_subscriptions_status CHECK (status IN ('PENDING_PAYMENT', 'ACTIVE', 'PAST_DUE', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_saas_subscriptions_dates CHECK (
        status IN ('PENDING_PAYMENT', 'CANCELLED') OR (start_at IS NOT NULL AND expires_at IS NOT NULL AND expires_at > start_at)
    ),
    CONSTRAINT ck_saas_subscriptions_grace CHECK (status <> 'PAST_DUE' OR grace_until IS NOT NULL)
);

CREATE INDEX ix_saas_subscriptions_due ON saas_subscriptions (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX ix_saas_subscriptions_grace ON saas_subscriptions (grace_until) WHERE status = 'PAST_DUE';
CREATE INDEX ix_saas_subscriptions_status ON saas_subscriptions (status);

CREATE TABLE saas_invoice_sequences (
    year       INTEGER PRIMARY KEY,
    last_value INTEGER NOT NULL
);

-- Cobro de un periodo del plan (no es un comprobante electrónico SUNAT).
CREATE TABLE saas_invoices (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number    VARCHAR(20)    NOT NULL,
    business_id       UUID           NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    subscription_id   UUID           NOT NULL REFERENCES saas_subscriptions (id) ON DELETE RESTRICT,
    plan_id           UUID           NOT NULL REFERENCES saas_plans (id) ON DELETE RESTRICT,
    plan_name_snapshot VARCHAR(80)   NOT NULL,
    billing_period    VARCHAR(10)    NOT NULL,
    kind              VARCHAR(15)    NOT NULL,
    amount            NUMERIC(12, 2) NOT NULL,
    status            VARCHAR(10)    NOT NULL DEFAULT 'OPEN',
    period_start      TIMESTAMPTZ,
    period_end        TIMESTAMPTZ,
    paid_at           TIMESTAMPTZ,
    voided_at         TIMESTAMPTZ,
    version           BIGINT         NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_saas_invoices_number UNIQUE (invoice_number),
    CONSTRAINT ck_saas_invoices_kind CHECK (kind IN ('NEW', 'RENEWAL', 'PLAN_CHANGE')),
    CONSTRAINT ck_saas_invoices_status CHECK (status IN ('OPEN', 'PAID', 'VOID')),
    CONSTRAINT ck_saas_invoices_period CHECK (billing_period IN ('MONTHLY', 'QUARTERLY', 'YEARLY')),
    CONSTRAINT ck_saas_invoices_amount CHECK (amount >= 0),
    CONSTRAINT ck_saas_invoices_paid CHECK (status <> 'PAID' OR (paid_at IS NOT NULL AND period_start IS NOT NULL AND period_end > period_start))
);

-- Como máximo un cobro abierto por negocio.
CREATE UNIQUE INDEX uq_saas_invoices_open ON saas_invoices (business_id) WHERE status = 'OPEN';
CREATE INDEX ix_saas_invoices_business ON saas_invoices (business_id, created_at DESC);
CREATE INDEX ix_saas_invoices_paid ON saas_invoices (paid_at) WHERE status = 'PAID';

CREATE TABLE saas_payments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id             UUID           NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    invoice_id              UUID           NOT NULL REFERENCES saas_invoices (id) ON DELETE RESTRICT,
    method                  VARCHAR(10)    NOT NULL,
    receiver_holder_snapshot VARCHAR(120)  NOT NULL,
    receiver_phone_snapshot VARCHAR(20)    NOT NULL,
    amount                  NUMERIC(12, 2) NOT NULL,
    transaction_reference   VARCHAR(64)    NOT NULL,
    receipt_key             VARCHAR(80),
    status                  VARCHAR(25)    NOT NULL DEFAULT 'PENDING_VERIFICATION',
    reported_at             TIMESTAMPTZ    NOT NULL,
    reported_by             UUID           NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    verified_at             TIMESTAMPTZ,
    verified_by             UUID REFERENCES users (id) ON DELETE RESTRICT,
    rejected_at             TIMESTAMPTZ,
    rejection_reason        VARCHAR(500),
    version                 BIGINT         NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT ck_saas_payments_method CHECK (method IN ('YAPE', 'PLIN')),
    CONSTRAINT ck_saas_payments_status CHECK (status IN ('PENDING_VERIFICATION', 'VERIFIED', 'REJECTED')),
    CONSTRAINT ck_saas_payments_verified CHECK (status <> 'VERIFIED' OR (verified_at IS NOT NULL AND verified_by IS NOT NULL)),
    CONSTRAINT ck_saas_payments_rejected CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

-- Un solo pago en revisión por cobro y un código de operación no se reutiliza.
CREATE UNIQUE INDEX uq_saas_payments_pending ON saas_payments (invoice_id) WHERE status = 'PENDING_VERIFICATION';
CREATE UNIQUE INDEX uq_saas_payments_reference ON saas_payments (method, transaction_reference)
    WHERE status IN ('PENDING_VERIFICATION', 'VERIFIED');
CREATE INDEX ix_saas_payments_status ON saas_payments (status, reported_at);
CREATE INDEX ix_saas_payments_business ON saas_payments (business_id, reported_at DESC);

-- Configuración general (una sola fila).
CREATE TABLE platform_settings (
    id               SMALLINT PRIMARY KEY DEFAULT 1,
    support_whatsapp VARCHAR(20),
    support_email    VARCHAR(254),
    saas_grace_days  INTEGER     NOT NULL DEFAULT 5,
    version          BIGINT      NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_platform_settings_single CHECK (id = 1),
    CONSTRAINT ck_platform_settings_grace CHECK (saas_grace_days BETWEEN 0 AND 30)
);

INSERT INTO platform_settings (id) VALUES (1);

-- Cuentas Yape/Plin de la PLATAFORMA (donde los vendedores pagan su plan).
CREATE TABLE platform_payment_methods (
    method       VARCHAR(10) PRIMARY KEY,
    holder_name  VARCHAR(120),
    phone        VARCHAR(20),
    qr_image_url VARCHAR(255),
    is_enabled   BOOLEAN     NOT NULL DEFAULT FALSE,
    version      BIGINT      NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_platform_payment_methods_method CHECK (method IN ('YAPE', 'PLIN')),
    CONSTRAINT ck_platform_payment_methods_enabled CHECK (NOT is_enabled OR (holder_name IS NOT NULL AND phone IS NOT NULL))
);

INSERT INTO platform_payment_methods (method) VALUES ('YAPE'), ('PLIN');
