-- El responsable asignado no necesariamente es quien hizo la acción. Se conserva al autor real
-- y la evidencia de auditoría de la resolución, sin atribuirla a quien consulte Pendientes.
ALTER TABLE seller_tasks
    ADD COLUMN resolved_by UUID REFERENCES users (id) ON DELETE RESTRICT,
    ADD COLUMN resolution_action VARCHAR(40),
    ADD COLUMN resolution_audit_id UUID REFERENCES audit_logs (id) ON DELETE RESTRICT;

-- Las resoluciones anteriores no tenían autor registrado. No se inventa ni se deduce del asignado.
UPDATE seller_tasks SET resolution_action = 'STATE_CHANGED' WHERE status = 'RESOLVED';

ALTER TABLE seller_tasks ADD CONSTRAINT ck_seller_tasks_resolution
    CHECK (status = 'RESOLVED' OR (resolved_by IS NULL AND resolution_action IS NULL AND resolution_audit_id IS NULL));

CREATE INDEX ix_seller_tasks_history ON seller_tasks (business_id, resolved_at DESC, id DESC)
    WHERE status = 'RESOLVED';
CREATE INDEX ix_audit_logs_entity ON audit_logs (business_id, entity_type, entity_id, created_at);
