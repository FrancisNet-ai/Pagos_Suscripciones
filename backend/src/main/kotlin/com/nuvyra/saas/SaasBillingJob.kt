package com.nuvyra.saas

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

/** Revisa los planes SaaS vencidos: ACTIVE → PAST_DUE → EXPIRED. Idempotente y por lotes. */
@Component
class SaasBillingJob(
    private val subscriptions: SaasSubscriptionRepository,
    private val billing: BillingService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.saas.job-interval:PT15M}", initialDelayString = "\${app.saas.job-initial-delay:PT2M}")
    fun run() {
        val lapsed = process({ subscriptions.findLapsed(clock.instant(), it) }, billing::markLapsed)
        val expired = process({ subscriptions.findGraceEnded(clock.instant(), it) }, billing::expireAfterGrace)
        if (lapsed + expired > 0) log.info("Planes SaaS vencidos: {}, sin vigencia tras la gracia: {}", lapsed, expired)
    }

    private fun process(find: (PageRequest) -> List<UUID>, action: (UUID) -> Boolean, batchSize: Int = 200): Int {
        var total = 0
        while (true) {
            val ids = find(PageRequest.of(0, batchSize))
            if (ids.isEmpty()) return total
            val changed = ids.count(action)
            total += changed
            if (ids.size < batchSize || changed == 0) return total
        }
    }
}
