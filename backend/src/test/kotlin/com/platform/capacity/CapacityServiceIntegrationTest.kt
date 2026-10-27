package com.platform.capacity

import com.platform.common.error.ApiException
import com.platform.products.Product
import com.platform.products.ProductRepository
import com.platform.products.ProductType
import com.platform.support.IntegrationTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CapacityServiceIntegrationTest : IntegrationTest() {
    @Autowired lateinit var capacity: CapacityService
    @Autowired lateinit var products: ProductRepository
    @Autowired lateinit var jdbc: JdbcTemplate

    private fun newProduct(capacityEnabled: Boolean = true, slots: Int? = 1): Product {
        registerSeller()
        val businessId = jdbc.queryForObject("select id from businesses order by created_at desc limit 1", UUID::class.java)!!
        return products.save(
            Product(
                businessId = businessId, name = "Servicio", description = null, category = null,
                productType = ProductType.SUBSCRIPTION, price = BigDecimal("15.00"), durationDays = 30,
                capacityEnabled = capacityEnabled, capacity = slots,
            ),
        )
    }

    private fun availability(product: Product) = capacity.availability(products.findById(product.id).get())

    @Test
    fun `producto sin control de cupos no reserva ni muestra cantidades`() {
        val product = newProduct(capacityEnabled = false, slots = null)
        assertThat(capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10))).isNull()
        assertThat(availability(product).available).isNull()
        assertThat(availability(product).soldOut).isFalse()
    }

    @Test
    fun `reserva hasta agotar y luego rechaza`() {
        val product = newProduct(slots = 2)
        repeat(2) { capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10)) }
        assertThat(availability(product).soldOut).isTrue()
        assertThatThrownBy { capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10)) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code").isEqualTo("PRODUCT_SOLD_OUT")
    }

    @Test
    fun `dos compras simultaneas del ultimo cupo solo obtienen uno`() {
        val product = newProduct(slots = 1)
        val threads = 8
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        val results = (1..threads).map {
            pool.submit<Boolean> {
                start.await()
                try {
                    capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10)) != null
                } catch (ex: ApiException) {
                    false
                }
            }
        }
        start.countDown()
        val successes = results.count { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertThat(successes).isEqualTo(1)
        assertThat(jdbc.queryForObject("select count(*) from capacity_allocations where product_id = ?", Int::class.java, product.id)).isEqualTo(1)
    }

    @Test
    fun `una reserva vencida deja de ocupar el cupo`() {
        val product = newProduct(slots = 1)
        val allocation = capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10))!!
        jdbc.update("update capacity_allocations set reserved_until = now() - interval '1 minute' where id = ?", allocation.id)
        assertThat(availability(product).available).isEqualTo(1)
        assertThat(capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10))).isNotNull()
    }

    @Test
    fun `una reserva vencida no se confirma si el cupo ya fue tomado`() {
        val product = newProduct(slots = 1)
        val late = capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10))!!
        jdbc.update("update capacity_allocations set reserved_until = now() - interval '1 minute' where id = ?", late.id)
        capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10))

        assertThatThrownBy { capacity.occupy(product.businessId, late.id) }
            .isInstanceOf(ApiException::class.java)
            .extracting("code").isEqualTo("PRODUCT_SOLD_OUT")
    }

    @Test
    fun `ocupar y liberar actualiza la disponibilidad`() {
        val product = newProduct(slots = 1)
        val allocation = capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10))!!
        capacity.occupy(product.businessId, allocation.id)
        assertThat(availability(product).occupied).isEqualTo(1)
        assertThat(availability(product).soldOut).isTrue()

        capacity.release(product.businessId, allocation.id)
        capacity.release(product.businessId, allocation.id) // idempotente
        assertThat(availability(product).available).isEqualTo(1)
    }

    @Test
    fun `no se puede operar sobre cupos de otro negocio`() {
        val product = newProduct(slots = 1)
        val allocation = capacity.reserve(product.businessId, product.id, Duration.ofMinutes(10))!!
        val otherBusiness = UUID.randomUUID()
        assertThatThrownBy { capacity.reserve(otherBusiness, product.id, Duration.ofMinutes(10)) }.isInstanceOf(ApiException::class.java)
        assertThatThrownBy { capacity.release(otherBusiness, allocation.id) }.isInstanceOf(ApiException::class.java)
    }
}
