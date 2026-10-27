package com.nuvyra.common.persistence

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PostLoad
import jakarta.persistence.PostPersist
import jakarta.persistence.Version
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

/**
 * Entidad con UUID asignado por la aplicación. Implementa [Persistable] para que Spring Data
 * haga `persist` (y no `merge` con un SELECT previo) al guardar entidades nuevas.
 */
@MappedSuperclass
abstract class AbstractEntity : Persistable<UUID> {
    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private var id: UUID = UuidV7.generate()

    @jakarta.persistence.Transient
    private var newEntity: Boolean = true

    override fun getId(): UUID = id

    override fun isNew(): Boolean = newEntity

    @PostPersist
    @PostLoad
    protected fun markPersisted() {
        newEntity = false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AbstractEntity) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/** Entidad editable: bloqueo optimista y marcas de tiempo. */
@MappedSuperclass
abstract class VersionedEntity : AbstractEntity() {
    @Version
    @Column(nullable = false)
    var version: Long = 0

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    var createdAt: Instant? = null

    @UpdateTimestamp
    @Column(nullable = false)
    var updatedAt: Instant? = null
}
