package com.platform.subscriptions

import com.platform.support.IntegrationTest
import com.platform.support.StoreFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate

/** Verifica el SQL de V4 que crea suscripciones para pedidos pagados antes de la Fase 4. */
class BackfillMigrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate

    @Test
    fun `los pedidos pagados en la fase 3 obtienen su suscripcion pendiente de activacion`() {
        val fx = StoreFixture(mockMvc)
        val store = fx.create(registerSeller(), capacity = 3)
        val orderId = fx.paidOrder(store)
        // Simula el estado previo a V4: pedido pagado sin suscripción.
        jdbc.update("update capacity_allocations set customer_subscription_id = null where order_id = ?::uuid", orderId)
        jdbc.update("delete from customer_subscriptions where order_id = ?::uuid", orderId)

        val migration = ClassPathResource("db/migration/V4__customer_subscriptions.sql").getContentAsString(Charsets.UTF_8)
        val backfill = migration.substringAfter("-- Pedidos ya pagados en la Fase 3").substringAfter('\n')
        backfill.split(";").map { it.trim() }.filter { it.isNotEmpty() }.forEach { jdbc.execute(it) }

        val row = jdbc.queryForMap("select status, origin, duration_days, contracted_price, paid_at, start_at from customer_subscriptions where order_id = ?::uuid", orderId)
        assertThat(row["status"]).isEqualTo("PENDING_ACTIVATION")
        assertThat(row["origin"]).isEqualTo("ORDER")
        assertThat(row["duration_days"]).isEqualTo(30)
        assertThat(row["paid_at"]).isNotNull()
        assertThat(row["start_at"]).isNull()
        assertThat(jdbc.queryForObject("select customer_subscription_id is not null from capacity_allocations where order_id = ?::uuid", Boolean::class.java, orderId)).isTrue()
    }
}
