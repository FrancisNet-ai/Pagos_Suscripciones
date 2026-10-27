package com.nuvyra.subscriptions

import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Clock

/** Marca como vencidas las suscripciones cuyo periodo terminó. Idempotente y por lotes. */
@Component
class SubscriptionExpirationJob(
    private val subscriptions: CustomerSubscriptionRepository,
    private val service: SubscriptionService,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT2M")
    fun run() {
        val count = expireOverdue()
        if (count > 0) log.info("Suscripciones vencidas: {}", count)
    }

    fun expireOverdue(batchSize: Int = 200): Int {
        var total = 0
        while (true) {
            val ids = subscriptions.findOverdue(clock.instant(), PageRequest.of(0, batchSize))
            if (ids.isEmpty()) return total
            val expired = ids.count { service.expire(it) }
            total += expired
            if (ids.size < batchSize || expired == 0) return total
        }
    }
}
