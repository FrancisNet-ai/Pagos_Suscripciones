-- La política se fija por solicitud: cambiar los ajustes del negocio solo afecta ciclos nuevos.
ALTER TABLE renewal_requests ADD COLUMN grace_expires_at TIMESTAMPTZ;
-- V5 emitía el token con un día adicional para consultar el resultado tras cerrar la gracia.
UPDATE renewal_requests SET grace_expires_at = token_expires_at - INTERVAL '1 day';
ALTER TABLE renewal_requests ALTER COLUMN grace_expires_at SET NOT NULL;
CREATE INDEX ix_renewal_requests_grace ON renewal_requests (grace_expires_at)
    WHERE status IN ('OPEN', 'AWAITING_PAYMENT');
