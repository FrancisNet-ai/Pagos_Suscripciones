package com.platform.customers

import com.platform.common.persistence.UuidV7
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.util.UUID

const val CONSENT_POLICY_VERSION = "2026-10"

@Service
class CustomerService(
    private val customers: CustomerRepository,
    private val consents: CustomerConsentRepository,
    private val jdbc: JdbcTemplate,
    private val clock: Clock,
) {
    /**
     * Busca el comprador por celular dentro del negocio o lo crea de forma atómica (dos pedidos
     * simultáneos con el mismo celular no fallan). No sobrescribe el nombre de un cliente existente:
     * cualquiera podría escribir el celular de otra persona. El pedido guarda el nombre ingresado.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun findOrCreateFromStorefront(businessId: UUID, fullName: String, phone: String): Customer {
        jdbc.update(
            """
            insert into customers (id, business_id, full_name, phone, source) values (?, ?, ?, ?, 'STOREFRONT')
            on conflict (business_id, phone) do nothing
            """.trimIndent(),
            UuidV7.generate(), businessId, fullName, phone,
        )
        return customers.findByBusinessIdAndPhone(businessId, phone)!!
    }

    @Transactional(propagation = Propagation.MANDATORY)
    fun recordConsents(customer: Customer, orderId: UUID, granted: Map<ConsentPurpose, Boolean>) {
        val now = clock.instant()
        consents.saveAll(
            granted.map { (purpose, value) ->
                CustomerConsent(customer.businessId, customer.id, orderId, purpose, value, "STOREFRONT", CONSENT_POLICY_VERSION, now)
            },
        )
    }
}
