-- Pendientes del vendedor. Cada tarea refleja una acción manual que exige el estado de los datos
-- (pago por verificar, servicio por activar, acceso por retirar, aviso por enviar). Se abre y se
-- resuelve sola al sincronizar con ese estado: no se puede «marcar como hecha» sin hacer la acción.
CREATE TABLE seller_tasks (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    business_id UUID        NOT NULL REFERENCES businesses (id) ON DELETE RESTRICT,
    task_type   VARCHAR(30) NOT NULL,
    -- Pago, suscripción, asignación de cupo o aviso según el tipo (sin FK: apunta a tablas distintas).
    entity_id   UUID        NOT NULL,
    status      VARCHAR(15) NOT NULL DEFAULT 'OPEN',
    -- Desde cuándo existe la necesidad (fecha del dato de origen) y plazo sugerido para atenderla.
    since       TIMESTAMPTZ NOT NULL,
    due_at      TIMESTAMPTZ NOT NULL,
    assigned_to UUID REFERENCES users (id) ON DELETE SET NULL,
    assigned_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_seller_tasks_type CHECK (
        task_type IN ('VERIFY_PAYMENT', 'REVIEW_INCIDENT', 'ACTIVATE_SERVICE', 'RELEASE_ACCESS', 'SEND_RENEWAL_NOTICE')
    ),
    CONSTRAINT ck_seller_tasks_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_seller_tasks_resolved CHECK (status = 'OPEN' OR resolved_at IS NOT NULL)
);

-- Una sola tarea abierta por necesidad: la sincronización es idempotente.
CREATE UNIQUE INDEX uq_seller_tasks_open ON seller_tasks (business_id, task_type, entity_id) WHERE status = 'OPEN';
CREATE INDEX ix_seller_tasks_business ON seller_tasks (business_id, status, due_at);
CREATE INDEX ix_seller_tasks_assignee ON seller_tasks (assigned_to) WHERE status = 'OPEN';
