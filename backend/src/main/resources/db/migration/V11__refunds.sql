-- Reembolsos manuales: el vendedor devuelve el dinero por su cuenta (Yape/Plin) y lo registra aquí.
-- Nunca se mueve dinero desde la plataforma; el registro deja de contar el pago como ingreso.
ALTER TABLE customer_payments
    ADD COLUMN refunded_at      TIMESTAMPTZ,
    ADD COLUMN refunded_by      UUID REFERENCES users (id) ON DELETE SET NULL,
    ADD COLUMN refund_reason    VARCHAR(300),
    ADD COLUMN refund_reference VARCHAR(40),
    ADD CONSTRAINT ck_customer_payments_refunded CHECK (status <> 'REFUNDED' OR (refunded_at IS NOT NULL AND refund_reason IS NOT NULL));

ALTER TABLE orders DROP CONSTRAINT ck_orders_status;
ALTER TABLE orders ADD CONSTRAINT ck_orders_status
    CHECK (status IN ('AWAITING_PAYMENT', 'PAYMENT_REPORTED', 'PAID', 'CANCELLED', 'EXPIRED', 'REFUNDED'));

-- Una renovación reembolsada devuelve la vigencia anterior y deja constancia del ciclo anulado.
ALTER TABLE subscription_renewals ADD COLUMN refunded_at TIMESTAMPTZ;
