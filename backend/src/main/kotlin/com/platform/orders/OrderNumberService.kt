package com.platform.orders

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.ZoneId
import java.util.UUID

/** Genera números legibles `ORD-2026-000128`, correlativos por negocio y año. */
@Service
class OrderNumberService(private val jdbc: JdbcTemplate, private val clock: Clock) {

    @Transactional(propagation = Propagation.MANDATORY)
    fun next(businessId: UUID, zone: ZoneId): String {
        val year = clock.instant().atZone(zone).year
        // Una sola sentencia atómica: la fila queda bloqueada hasta el fin de la transacción.
        val value = jdbc.queryForObject(
            """
            insert into order_number_sequences (business_id, year, last_value) values (?, ?, 1)
            on conflict (business_id, year) do update set last_value = order_number_sequences.last_value + 1
            returning last_value
            """.trimIndent(),
            Int::class.java, businessId, year,
        )!!
        return format(year, value)
    }

    companion object {
        fun format(year: Int, value: Int) = "ORD-$year-${value.toString().padStart(6, '0')}"
    }
}
