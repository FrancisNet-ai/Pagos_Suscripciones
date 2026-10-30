-- Row Level Security, etapa 5 de ADR-007: la política de V17 se extiende al resto de tablas de cada negocio.
-- Quedan fuera, a propósito:
--   audit_logs              hay registros sin negocio (inicios de sesión, acciones de plataforma);
--   business_members        la lista de negocios de una persona cruza negocios por diseño;
--   whatsapp_webhook_events solo la escribe el webhook de Meta, que no corre como negocio;
--   whatsapp_integrations   al conectar un número se comprueba que ningún otro negocio lo use;
--   saas_payments           el número de operación de Yape/Plin no puede repetirse entre negocios (antifraude).

ALTER TABLE products ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON products TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE capacity_allocations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON capacity_allocations TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE business_payment_methods ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON business_payment_methods TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE seller_tasks ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON seller_tasks TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE customer_imports ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer_imports TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);


ALTER TABLE order_notifications ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON order_notifications TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE notification_preferences ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON notification_preferences TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE staff_invitations ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON staff_invitations TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE support_grants ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON support_grants TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE order_number_sequences ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON order_number_sequences TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE saas_subscriptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON saas_subscriptions TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE saas_invoices ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON saas_invoices TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);


-- order_items no tiene business_id: hereda el aislamiento de su pedido, que ya está protegido.
ALTER TABLE order_items ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON order_items TO nuvyra_app
    USING (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_items.order_id))
    WITH CHECK (EXISTS (SELECT 1 FROM orders o WHERE o.id = order_items.order_id));
