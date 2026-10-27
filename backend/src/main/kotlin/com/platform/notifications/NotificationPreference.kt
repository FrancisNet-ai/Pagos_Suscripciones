package com.platform.notifications

import com.platform.common.persistence.VersionedEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** Exclusión de comunicaciones por WhatsApp. Puede pedirla el cliente o registrarla el vendedor. */
@Entity
@Table(name = "notification_preferences")
class NotificationPreference(
    @Column(nullable = false, updatable = false) val businessId: UUID,
    @Column(nullable = false, updatable = false) val customerId: UUID,
    @Column(nullable = false) var optedOut: Boolean,
    @Column(nullable = false) var source: String,
    @Column(nullable = false, updatable = false) val channel: String = "WHATSAPP",
) : VersionedEntity()

interface NotificationPreferenceRepository : JpaRepository<NotificationPreference, java.util.UUID> {
    fun findByCustomerIdAndChannel(customerId: UUID, channel: String = "WHATSAPP"): NotificationPreference?
}
