-- Varios negocios por cuenta: una persona puede ser titular de más de un negocio o colaborar en otros.
-- La sesión recuerda el negocio activo; el servidor siempre verifica la membresía en cada petición.
DROP INDEX uq_business_members_user;
-- (V1 ya creó ix_business_members_user sobre user_id, que sirve para listar las membresías.)

ALTER TABLE user_sessions ADD COLUMN active_business_id UUID REFERENCES businesses (id) ON DELETE SET NULL;
