-- Prueba gratuita: el primer plan de pago empieza con 14 días sin cobro (cobro de S/ 0 «Prueba gratuita»).
ALTER TABLE saas_invoices DROP CONSTRAINT ck_saas_invoices_kind;
ALTER TABLE saas_invoices ADD CONSTRAINT ck_saas_invoices_kind
    CHECK (kind IN ('NEW', 'RENEWAL', 'PLAN_CHANGE', 'COMPLIMENTARY', 'TRIAL'));

-- Una prueba por celular del titular y por negocio. Sin RLS a propósito (como saas_payments): hay que saber si
-- ese celular ya la usó con otro negocio. Solo guarda el celular normalizado y el negocio.
CREATE TABLE saas_trial_claims (
    phone       VARCHAR(20) PRIMARY KEY,
    business_id UUID        NOT NULL UNIQUE REFERENCES businesses (id) ON DELETE CASCADE,
    claimed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
