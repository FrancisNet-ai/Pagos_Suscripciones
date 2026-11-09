package com.nuvyra.common.tenancy

import java.util.UUID

/** Negocio cuyas filas puede tocar el hilo actual (Row Level Security). `null`: sin restricción de la base. */
object TenantContext {
    private val current = ThreadLocal<UUID?>()

    fun get(): UUID? = current.get()

    fun <T> runAs(businessId: UUID?, block: () -> T): T {
        val previous = current.get()
        current.set(businessId)
        try {
            return block()
        } finally {
            if (previous == null) current.remove() else current.set(previous)
        }
    }
}
