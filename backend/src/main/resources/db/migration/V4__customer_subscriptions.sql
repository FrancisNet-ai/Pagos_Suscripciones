-- Fase 4: suscripciones de compradores. Distingue fecha de pago, activación y vencimiento.

CREATE TABLE customer_subscriptions (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id           UUID          NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    customer_id           UUID          NOT NULL REFERENCES customers (id) ON DELETE RESTRICT,
    product_id            UUID          NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    order_id              UUID REFERENCES orders (id) ON DELETE RESTRICT,
    origin                VARCHAR(10)   NOT NULL,
    product_name_snapshot VARCHAR(120)  NOT NULL,
    duration_days         INTEGER       NOT NULL,
    contracted_price      NUMERIC(10, 2) NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    paid_at               TIMESTAMPTZ,
    start_at              TIMESTAMPTZ,
    expires_at            TIMESTAMPTZ,
    last_renewed_at       TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    cancellation_reason   VARCHAR(300),
    notes                 VARCHAR(1000),
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_customer_subscriptions_origin CHECK (origin IN ('ORDER', 'MANUAL', 'IMPORT')),
    CONSTRAINT ck_customer_subscriptions_status CHECK (
        status IN ('PENDING_ACTIVATION', 'ACTIVE', 'RENEWAL_PENDING', 'EXPIRED', 'CANCELLED', 'ENDED')
    ),
    CONSTRAINT ck_customer_subscriptions_duration CHECK (duration_days BETWEEN 1 AND 3650),
    CONSTRAINT ck_customer_subscriptions_price CHECK (contracted_price >= 0),
    -- Una suscripción activa o vencida siempre tiene fechas reales de inicio y fin.
    CONSTRAINT ck_customer_subscriptions_dates CHECK (
        status IN ('PENDING_ACTIVATION', 'CANCELLED') OR (start_at IS NOT NULL AND expires_at IS NOT NULL AND expires_at > start_at)
    ),
    -- Las suscripciones de pedidos nacen de un pago; las antiguas no.
    CONSTRAINT ck_customer_subscriptions_order CHECK ((origin = 'ORDER') = (order_id IS NOT NULL))
);

CREATE UNIQUE INDEX ux_customer_subscriptions_order ON customer_subscriptions (order_id) WHERE order_id IS NOT NULL;
-- Evita registrar dos veces la misma suscripción antigua.
CREATE UNIQUE INDEX ux_customer_subscriptions_manual ON customer_subscriptions (business_id, customer_id, product_id, start_at)
    WHERE origin <> 'ORDER' AND status <> 'CANCELLED';
CREATE INDEX ix_customer_subscriptions_business_status ON customer_subscriptions (business_id, status, expires_at);
CREATE INDEX ix_customer_subscriptions_customer ON customer_subscriptions (customer_id);
-- Tarea de vencimiento: activas ordenadas por fecha de fin.
CREATE INDEX ix_customer_subscriptions_expiring ON customer_subscriptions (expires_at) WHERE status = 'ACTIVE';

ALTER TABLE capacity_allocations ADD COLUMN customer_subscription_id UUID REFERENCES customer_subscriptions (id) ON DELETE RESTRICT;
CREATE INDEX ix_capacity_allocations_subscription ON capacity_allocations (customer_subscription_id);

-- Pedidos ya pagados en la Fase 3: crear su suscripción pendiente de activación.
INSERT INTO customer_subscriptions (business_id, customer_id, product_id, order_id, origin, product_name_snapshot,
                                    duration_days, contracted_price, status, paid_at)
SELECT o.business_id, o.customer_id, i.product_id, o.id, 'ORDER', i.product_name_snapshot,
       i.duration_snapshot, i.unit_price_snapshot, 'PENDING_ACTIVATION', o.paid_at
FROM orders o
JOIN order_items i ON i.order_id = o.id
WHERE o.status = 'PAID' AND i.product_type_snapshot = 'SUBSCRIPTION' AND i.duration_snapshot IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM customer_subscriptions s WHERE s.order_id = o.id);

UPDATE capacity_allocations a
SET customer_subscription_id = s.id
FROM customer_subscriptions s
WHERE s.order_id = a.order_id AND a.customer_subscription_id IS NULL;
