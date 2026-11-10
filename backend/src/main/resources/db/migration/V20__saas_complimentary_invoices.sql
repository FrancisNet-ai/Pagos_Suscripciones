-- Cobros de cortesía: plan otorgado por el SUPER_ADMIN sin pago (monto 0), auditado con su motivo.
ALTER TABLE saas_invoices DROP CONSTRAINT ck_saas_invoices_kind;
ALTER TABLE saas_invoices ADD CONSTRAINT ck_saas_invoices_kind CHECK (kind IN ('NEW', 'RENEWAL', 'PLAN_CHANGE', 'COMPLIMENTARY'));
