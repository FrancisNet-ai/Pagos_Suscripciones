-- Fase 2: métodos de cobro del vendedor, catálogo de productos y cupos.

CREATE TABLE business_payment_methods (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id   UUID         NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    method        VARCHAR(10)  NOT NULL,
    holder_name   VARCHAR(120) NOT NULL,
    phone         VARCHAR(16)  NOT NULL,
    qr_image_url  VARCHAR(500),
    is_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    version       BIGINT       NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_business_payment_methods UNIQUE (business_id, method),
    CONSTRAINT ck_business_payment_methods_method CHECK (method IN ('YAPE', 'PLIN')),
    CONSTRAINT ck_business_payment_methods_phone CHECK (phone ~ '^\+[1-9][0-9]{7,14}$')
);

CREATE TABLE products (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id        UUID          NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    name               VARCHAR(120)  NOT NULL,
    description        VARCHAR(2000),
    category           VARCHAR(60),
    product_type       VARCHAR(20)   NOT NULL,
    price              NUMERIC(10, 2) NOT NULL,
    duration_days      INTEGER,
    image_url          VARCHAR(500),
    capacity_enabled   BOOLEAN       NOT NULL DEFAULT FALSE,
    capacity           INTEGER,
    publication_status VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    is_available       BOOLEAN       NOT NULL DEFAULT TRUE,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_products_type CHECK (product_type IN ('SUBSCRIPTION', 'ONE_TIME')),
    CONSTRAINT ck_products_price CHECK (price > 0),
    CONSTRAINT ck_products_duration CHECK (duration_days IS NULL OR duration_days BETWEEN 1 AND 3650),
    CONSTRAINT ck_products_subscription_duration CHECK (product_type <> 'SUBSCRIPTION' OR duration_days IS NOT NULL),
    CONSTRAINT ck_products_capacity CHECK (
        (capacity_enabled AND capacity BETWEEN 1 AND 100000) OR (NOT capacity_enabled AND capacity IS NULL)
    ),
    CONSTRAINT ck_products_publication CHECK (publication_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
);

CREATE INDEX ix_products_business_status ON products (business_id, publication_status, created_at DESC);

-- Asignaciones de cupos. Las referencias a pedidos y suscripciones se añaden en fases posteriores.
CREATE TABLE capacity_allocations (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id       UUID        NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    product_id        UUID        NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    allocation_status VARCHAR(20) NOT NULL,
    reserved_until    TIMESTAMPTZ,
    released_at       TIMESTAMPTZ,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_capacity_allocations_status CHECK (
        allocation_status IN ('RESERVED', 'OCCUPIED', 'RELEASE_PENDING', 'RELEASED', 'EXPIRED')
    ),
    CONSTRAINT ck_capacity_allocations_reserved CHECK (allocation_status <> 'RESERVED' OR reserved_until IS NOT NULL)
);

-- Solo las asignaciones que pueden ocupar cupo se consultan al calcular disponibilidad.
CREATE INDEX ix_capacity_allocations_active ON capacity_allocations (product_id, allocation_status, reserved_until)
    WHERE allocation_status IN ('RESERVED', 'OCCUPIED', 'RELEASE_PENDING');
CREATE INDEX ix_capacity_allocations_business ON capacity_allocations (business_id);
