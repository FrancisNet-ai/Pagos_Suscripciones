-- Confirmaciones de pedido y de pago por la integración oficial de WhatsApp. Solo a compradores que lo
-- autorizaron al comprar (ORDER_UPDATES) y con plantillas aprobadas por Meta para cada mensaje.
ALTER TABLE whatsapp_integrations
    ADD COLUMN order_template   VARCHAR(512),
    ADD COLUMN payment_template VARCHAR(512);

ALTER TABLE customer_consents DROP CONSTRAINT ck_customer_consents_purpose;
ALTER TABLE customer_consents ADD CONSTRAINT ck_customer_consents_purpose
    CHECK (purpose IN ('PURCHASE_TERMS', 'DATA_PROCESSING', 'RENEWAL_REMINDERS', 'MARKETING_WHATSAPP', 'ORDER_UPDATES'));

CREATE TABLE order_notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id         UUID         NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    order_id            UUID         NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    kind                VARCHAR(20)  NOT NULL,
    status              VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    attempts            INTEGER      NOT NULL DEFAULT 0,
    scheduled_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at             TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    read_at             TIMESTAMPTZ,
    provider_message_id VARCHAR(120),
    failure_reason      VARCHAR(500),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Un mensaje de cada tipo por pedido, aunque el pago se reporte de nuevo tras un rechazo.
    CONSTRAINT uq_order_notifications UNIQUE (order_id, kind),
    CONSTRAINT ck_order_notifications_kind CHECK (kind IN ('PAYMENT_RECEIVED', 'PAYMENT_VERIFIED')),
    CONSTRAINT ck_order_notifications_status CHECK (status IN ('PENDING', 'SENT', 'DELIVERED', 'FAILED'))
);

CREATE INDEX ix_order_notifications_due ON order_notifications (scheduled_at) WHERE status = 'PENDING';
CREATE UNIQUE INDEX uq_order_notifications_provider ON order_notifications (provider_message_id) WHERE provider_message_id IS NOT NULL;
