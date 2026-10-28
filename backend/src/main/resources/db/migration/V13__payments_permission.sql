-- Permiso propio para aprobar o rechazar pagos, separado de ver y atender pedidos. Quien ya tenía
-- «Pedidos» conserva exactamente lo que podía hacer: recibe también el permiso nuevo.
UPDATE business_members
SET permissions = regexp_replace(permissions, '(^|,)ORDERS(,|$)', '\1ORDERS,PAYMENTS\2')
WHERE role = 'STAFF' AND permissions ~ '(^|,)ORDERS(,|$)';

UPDATE staff_invitations
SET permissions = regexp_replace(permissions, '(^|,)ORDERS(,|$)', '\1ORDERS,PAYMENTS\2')
WHERE status = 'PENDING' AND permissions ~ '(^|,)ORDERS(,|$)';
