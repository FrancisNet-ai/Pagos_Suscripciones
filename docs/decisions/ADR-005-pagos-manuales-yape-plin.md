# ADR-005 — Verificación manual de Yape/Plin y `PaymentProvider`

**Estado:** aceptado · **Fecha:** 2026-10-27

## Contexto
Yape y Plin no ofrecen una API pública para que terceros verifiquen transferencias entre personas.
La plataforma no debe actuar como intermediario financiero ni simular verificaciones.

## Decisión
* El comprador paga directamente al vendedor y reporta el código de operación (y opcionalmente un
  comprobante). El pago pasa a `PENDING_VERIFICATION`.
* Solo una aprobación explícita del vendedor (o, en el futuro, un proveedor autorizado) produce `VERIFIED`.
* Interfaz `PaymentProvider` con implementación `ManualVerificationProvider`. Las integraciones automáticas
  se añadirán solo con APIs oficiales que confirmen transacciones reales.
* No se leen capturas de pantalla para verificar pagos.
