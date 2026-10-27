-- Fase 3: compradores, consentimientos, pedidos y pagos de compradores (ventas del vendedor).
-- Nada de esto pertenece a la facturación SaaS (tablas saas_*).

CREATE TABLE customers (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID         NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    full_name   VARCHAR(120) NOT NULL,
    phone       VARCHAR(16)  NOT NULL,
    source      VARCHAR(20)  NOT NULL,
    notes       VARCHAR(1000),
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Un mismo celular puede ser cliente de varios negocios: la unicidad es por negocio.
    CONSTRAINT uq_customers_business_phone UNIQUE (business_id, phone),
    CONSTRAINT ck_customers_phone CHECK (phone ~ '^\+[1-9][0-9]{7,14}$'),
    CONSTRAINT ck_customers_source CHECK (source IN ('STOREFRONT', 'MANUAL', 'IMPORT'))
);

CREATE INDEX ix_customers_business_name ON customers (business_id, lower(full_name));

CREATE TABLE order_number_sequences (
    business_id UUID    NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    year        INTEGER NOT NULL,
    last_value  INTEGER NOT NULL,
    PRIMARY KEY (business_id, year)
);

CREATE TABLE orders (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id             UUID          NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    order_number            VARCHAR(20)   NOT NULL,
    customer_id             UUID          NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    customer_name_snapshot  VARCHAR(120)  NOT NULL,
    customer_phone_snapshot VARCHAR(16)   NOT NULL,
    kind                    VARCHAR(20)   NOT NULL DEFAULT 'PURCHASE',
    status                  VARCHAR(20)   NOT NULL,
    total_amount            NUMERIC(12, 2) NOT NULL,
    currency                VARCHAR(3)    NOT NULL DEFAULT 'PEN',
    public_token_hash       VARCHAR(64)   NOT NULL,
    idempotency_key         UUID,
    incident_reason         VARCHAR(300),
    cancellation_reason     VARCHAR(300),
    paid_at                 TIMESTAMPTZ,
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_number UNIQUE (business_id, order_number),
    CONSTRAINT uq_orders_public_token UNIQUE (public_token_hash),
    CONSTRAINT uq_orders_idempotency UNIQUE (business_id, idempotency_key),
    CONSTRAINT ck_orders_kind CHECK (kind IN ('PURCHASE', 'RENEWAL')),
    CONSTRAINT ck_orders_status CHECK (status IN ('AWAITING_PAYMENT', 'PAYMENT_REPORTED', 'PAID', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT ck_orders_amount CHECK (total_amount > 0)
);

CREATE INDEX ix_orders_business_created ON orders (business_id, created_at DESC);
CREATE INDEX ix_orders_business_status ON orders (business_id, status, created_at DESC);
CREATE INDEX ix_orders_customer ON orders (customer_id);
-- Tarea de expiración: pedidos sin pago ordenados por antigüedad.
CREATE INDEX ix_orders_awaiting ON orders (created_at) WHERE status = 'AWAITING_PAYMENT';

CREATE TABLE order_items (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id              UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id            UUID          NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    product_name_snapshot VARCHAR(120)  NOT NULL,
    product_type_snapshot VARCHAR(20)   NOT NULL,
    unit_price_snapshot   NUMERIC(10, 2) NOT NULL,
    duration_snapshot     INTEGER,
    quantity              INTEGER       NOT NULL DEFAULT 1,
    CONSTRAINT ck_order_items_quantity CHECK (quantity > 0)
);

CREATE INDEX ix_order_items_order ON order_items (order_id);
CREATE INDEX ix_order_items_product ON order_items (product_id);

CREATE TABLE customer_consents (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id    UUID        NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    customer_id    UUID        NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    order_id       UUID REFERENCES orders (id) ON DELETE SET NULL,
    purpose        VARCHAR(30) NOT NULL,
    granted        BOOLEAN     NOT NULL,
    source         VARCHAR(20) NOT NULL,
    policy_version VARCHAR(20) NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at     TIMESTAMPTZ,
    CONSTRAINT ck_customer_consents_purpose CHECK (purpose IN ('PURCHASE_TERMS', 'DATA_PROCESSING', 'RENEWAL_REMINDERS', 'MARKETING_WHATSAPP'))
);

CREATE INDEX ix_customer_consents_customer ON customer_consents (customer_id, purpose, created_at DESC);

CREATE TABLE customer_payments (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id             UUID          NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    order_id                UUID          NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    payment_method_id       UUID REFERENCES business_payment_methods (id) ON DELETE SET NULL,
    method_snapshot         VARCHAR(10)   NOT NULL,
    holder_name_snapshot    VARCHAR(120)  NOT NULL,
    receiver_phone_snapshot VARCHAR(16)   NOT NULL,
    amount                  NUMERIC(12, 2) NOT NULL,
    transaction_reference   VARCHAR(40),
    receipt_key             VARCHAR(60),
    status                  VARCHAR(25)   NOT NULL,
    reported_at             TIMESTAMPTZ,
    verified_at             TIMESTAMPTZ,
    verified_by             UUID REFERENCES users (id) ON DELETE SET NULL,
    rejected_at             TIMESTAMPTZ,
    rejection_reason        VARCHAR(300),
    version                 BIGINT        NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_customer_payments_method CHECK (method_snapshot IN ('YAPE', 'PLIN')),
    CONSTRAINT ck_customer_payments_status CHECK (
        status IN ('PENDING', 'REPORTED', 'PENDING_VERIFICATION', 'VERIFIED', 'REJECTED', 'CANCELLED', 'REFUNDED')
    ),
    -- Un voucher o código nunca basta: VERIFIED exige quién y cuándo lo aprobó.
    CONSTRAINT ck_customer_payments_verified CHECK (status <> 'VERIFIED' OR (verified_at IS NOT NULL AND verified_by IS NOT NULL))
);

CREATE INDEX ix_customer_payments_order ON customer_payments (order_id, created_at DESC);
CREATE INDEX ix_customer_payments_business_status ON customer_payments (business_id, status, reported_at);
-- Un mismo código de operación no puede usarse en dos pagos vigentes del mismo negocio y método.
CREATE UNIQUE INDEX ux_customer_payments_reference ON customer_payments (business_id, method_snapshot, transaction_reference)
    WHERE status IN ('PENDING_VERIFICATION', 'VERIFIED');

ALTER TABLE capacity_allocations ADD COLUMN order_id UUID REFERENCES orders (id) ON DELETE RESTRICT;
CREATE INDEX ix_capacity_allocations_order ON capacity_allocations (order_id);
