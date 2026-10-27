-- Fase 5: renovaciones, avisos y preferencias de comunicación.

-- Política de recordatorios configurable por negocio.
ALTER TABLE businesses
    ADD COLUMN reminder_days_before INTEGER NOT NULL DEFAULT 3,
    ADD COLUMN reminder_on_due_day  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN renewal_grace_days   INTEGER NOT NULL DEFAULT 3,
    ADD CONSTRAINT ck_businesses_reminder_days CHECK (reminder_days_before BETWEEN 0 AND 15),
    ADD CONSTRAINT ck_businesses_grace_days CHECK (renewal_grace_days BETWEEN 0 AND 30);

-- Una solicitud por suscripción y ciclo (fecha de vencimiento que se intenta renovar).
CREATE TABLE renewal_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id      UUID        NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    subscription_id  UUID        NOT NULL REFERENCES customer_subscriptions (id) ON DELETE RESTRICT,
    customer_id      UUID        NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    product_id       UUID        NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    cycle_expires_at TIMESTAMPTZ NOT NULL,
    token_hash       VARCHAR(64) NOT NULL,
    token_expires_at TIMESTAMPTZ NOT NULL,
    response         VARCHAR(10) NOT NULL DEFAULT 'NONE',
    status           VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    responded_at     TIMESTAMPTZ,
    completed_at     TIMESTAMPTZ,
    order_id         UUID REFERENCES orders (id) ON DELETE SET NULL,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_renewal_requests_cycle UNIQUE (subscription_id, cycle_expires_at),
    CONSTRAINT uq_renewal_requests_token UNIQUE (token_hash),
    CONSTRAINT ck_renewal_requests_response CHECK (response IN ('NONE', 'ACCEPTED', 'DECLINED')),
    CONSTRAINT ck_renewal_requests_status CHECK (status IN ('OPEN', 'AWAITING_PAYMENT', 'COMPLETED', 'DECLINED', 'EXPIRED'))
);

CREATE INDEX ix_renewal_requests_business ON renewal_requests (business_id, status, cycle_expires_at);
CREATE INDEX ix_renewal_requests_open ON renewal_requests (cycle_expires_at) WHERE status IN ('OPEN', 'AWAITING_PAYMENT');

CREATE TABLE renewal_notifications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id         UUID        NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    renewal_request_id  UUID        NOT NULL REFERENCES renewal_requests (id) ON DELETE CASCADE,
    customer_id         UUID        NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    notification_type   VARCHAR(20) NOT NULL,
    channel             VARCHAR(20) NOT NULL DEFAULT 'MANUAL_LINK',
    scheduled_at        TIMESTAMPTZ NOT NULL,
    sent_at             TIMESTAMPTZ,
    sent_by             UUID REFERENCES users (id) ON DELETE SET NULL,
    delivery_status     VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    provider_message_id VARCHAR(120),
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Nunca dos avisos del mismo tipo para la misma solicitud.
    CONSTRAINT uq_renewal_notifications UNIQUE (renewal_request_id, notification_type),
    CONSTRAINT ck_renewal_notifications_type CHECK (notification_type IN ('REMINDER_BEFORE', 'REMINDER_DUE_DAY', 'FAREWELL')),
    CONSTRAINT ck_renewal_notifications_channel CHECK (channel IN ('MANUAL_LINK', 'WHATSAPP_API')),
    CONSTRAINT ck_renewal_notifications_status CHECK (delivery_status IN ('PENDING', 'SENT_MANUALLY', 'SENT', 'DELIVERED', 'FAILED', 'SKIPPED'))
);

CREATE INDEX ix_renewal_notifications_due ON renewal_notifications (business_id, scheduled_at) WHERE delivery_status = 'PENDING';

CREATE TABLE notification_preferences (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID        NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    customer_id UUID        NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    channel     VARCHAR(20) NOT NULL DEFAULT 'WHATSAPP',
    opted_out   BOOLEAN     NOT NULL,
    source      VARCHAR(20) NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notification_preferences UNIQUE (customer_id, channel),
    CONSTRAINT ck_notification_preferences_source CHECK (source IN ('CUSTOMER', 'SELLER'))
);

-- Historial de renovaciones confirmadas.
CREATE TABLE subscription_renewals (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id         UUID          NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    subscription_id     UUID          NOT NULL REFERENCES customer_subscriptions (id) ON DELETE RESTRICT,
    order_id            UUID          NOT NULL REFERENCES orders (id) ON DELETE RESTRICT,
    renewal_request_id  UUID REFERENCES renewal_requests (id) ON DELETE SET NULL,
    previous_expires_at TIMESTAMPTZ   NOT NULL,
    new_expires_at      TIMESTAMPTZ   NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_subscription_renewals_order UNIQUE (order_id),
    CONSTRAINT ck_subscription_renewals_dates CHECK (new_expires_at > previous_expires_at)
);

CREATE INDEX ix_subscription_renewals_subscription ON subscription_renewals (subscription_id, created_at DESC);
CREATE INDEX ix_subscription_renewals_business ON subscription_renewals (business_id, created_at DESC);

ALTER TABLE orders ADD COLUMN renewal_request_id UUID REFERENCES renewal_requests (id) ON DELETE SET NULL;
ALTER TABLE orders ADD CONSTRAINT ck_orders_renewal CHECK ((kind = 'RENEWAL') = (renewal_request_id IS NOT NULL));
