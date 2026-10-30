-- Row Level Security, primera etapa de ADR-007: segunda barrera de aislamiento entre negocios.
--
-- La aplicación se conecta con un usuario administrador (dueño del esquema, ejecuta Flyway) y los
-- administradores ignoran RLS. Por eso existe el rol nuvyra_app: sin privilegios de administración, solo
-- con permisos sobre los datos. En cada petición del panel de un negocio, la conexión pasa a ese rol y fija
-- app.business_id (common/tenancy/TenantAwareDataSource). Las políticas solo dejan ver y escribir filas de
-- ese negocio, aunque una consulta olvide filtrar por business_id; sin negocio fijado no devuelven nada.
-- El resto de rutas (tareas programadas, webhooks, tienda pública, enlaces por token, superadministración
-- y Flyway) sigue con el usuario dueño y el aislamiento que ya aplica la aplicación.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nuvyra_app') THEN
        CREATE ROLE nuvyra_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOBYPASSRLS;
    END IF;
END
$$;

-- El usuario de la aplicación debe poder cambiar a ese rol (si es administrador ya puede).
GRANT nuvyra_app TO CURRENT_USER;

GRANT USAGE ON SCHEMA public TO nuvyra_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO nuvyra_app;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA public TO nuvyra_app;
-- Las tablas de migraciones futuras quedan con los mismos permisos.
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO nuvyra_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT, UPDATE ON SEQUENCES TO nuvyra_app;

-- Tablas con datos personales de compradores (etapa 2 del ADR).

ALTER TABLE customers ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customers TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE customer_consents ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer_consents TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON orders TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE customer_payments ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer_payments TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE customer_subscriptions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON customer_subscriptions TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE renewal_requests ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON renewal_requests TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE renewal_notifications ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON renewal_notifications TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);

ALTER TABLE subscription_renewals ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON subscription_renewals TO nuvyra_app
    USING (business_id = nullif(current_setting('app.business_id', true), '')::uuid)
    WITH CHECK (business_id = nullif(current_setting('app.business_id', true), '')::uuid);
