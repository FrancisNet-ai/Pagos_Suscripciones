package com.nuvyra.renewals

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Crea solicitudes de renovación para suscripciones próximas a vencer y cierra las que superaron
 * el plazo de gracia. Idempotente (restricciones únicas por ciclo y por tipo de aviso) y por lotes.
 */
@Component
class RenewalJob(private val service: RenewalService) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.renewals.job-interval:PT15M}", initialDelayString = "\${app.renewals.job-initial-delay:PT3M}")
    fun run() {
        val opened = openDueRequests()
        val expired = expireStale()
        if (opened + expired > 0) log.info("Renovaciones: {} solicitudes nuevas, {} cerradas sin pago", opened, expired)
    }

    fun openDueRequests(batchSize: Int = 200): Int = drain(batchSize, service::dueSubscriptions, service::openRequest)

    fun expireStale(batchSize: Int = 200): Int = drain(batchSize, service::staleRequests, service::expire)

    private fun drain(batchSize: Int, next: (Int) -> List<java.util.UUID>, process: (java.util.UUID) -> Boolean): Int {
        var total = 0
        while (true) {
            val ids = next(batchSize)
            if (ids.isEmpty()) return total
            val done = ids.count { process(it) }
            total += done
            if (ids.size < batchSize || done == 0) return total
        }
    }
}
