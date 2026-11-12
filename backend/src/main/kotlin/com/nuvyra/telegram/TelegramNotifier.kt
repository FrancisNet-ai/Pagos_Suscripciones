package com.nuvyra.telegram

import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditRecordedEvent
import com.nuvyra.common.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID

/** Un aviso listo para enviar a un chat. */
data class TelegramNotice(val chatId: Long, val text: String, val buttonText: String, val buttonUrl: String)

/**
 * Avisa por Telegram a quien conectó su cuenta: al titular, de pedidos nuevos y pagos reportados; al
 * superadministrador, de registros, pruebas gratis y pagos de planes. Escucha la auditoría después del commit
 * (nada se avisa si la operación se revirtió) y envía en segundo plano: un Telegram lento o caído nunca afecta
 * la compra ni el registro.
 */
@Component
class TelegramNotifier(
    private val properties: AppProperties,
    private val jdbc: JdbcTemplate,
    private val client: TelegramClient,
    @param:Qualifier("applicationTaskExecutor") private val executor: TaskExecutor,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onAudit(event: AuditRecordedEvent) {
        if (!properties.telegram.enabled || event.entry.action !in WATCHED) return
        val entry = event.entry
        executor.execute {
            try {
                noticesFor(entry.action, entry.businessId, entry.entityId).forEach { client.sendMessage(it.chatId, it.text, it.buttonText, it.buttonUrl) }
            } catch (ex: Exception) {
                log.warn("No se pudo preparar el aviso de Telegram ({}): {}", entry.action, ex.javaClass.simpleName)
            }
        }
    }

    /** Arma los avisos de un evento. Corre fuera de la petición, sin negocio en el hilo: lee como la plataforma. */
    fun noticesFor(action: String, businessId: UUID?, entityId: UUID?): List<TelegramNotice> {
        val base = properties.publicBaseUrl.trimEnd('/')
        return when (action) {
            AuditActions.ORDER_CREATED, AuditActions.RENEWAL_ORDER_CREATED -> {
                val orderId = entityId ?: return emptyList()
                val o = jdbc.query(
                    """
                    select o.order_number, o.customer_name_snapshot, o.total_amount, o.business_id,
                           (select string_agg(i.product_name_snapshot, ', ') from order_items i where i.order_id = o.id) products
                    from orders o where o.id = ?
                    """.trimIndent(),
                    { rs, _ -> listOf(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getObject(4, UUID::class.java), rs.getString(5)) },
                    orderId,
                ).firstOrNull() ?: return emptyList()
                val title = if (action == AuditActions.RENEWAL_ORDER_CREATED) "🔁 Renovación pedida" else "🛒 Nuevo pedido"
                val text = "$title ${o[0]}\n${o[1]} · ${o[4] ?: "Producto"}\nTotal: ${soles(o[2] as BigDecimal)}\n\nTe avisaremos cuando reporte su pago."
                ownerChats(o[3] as UUID).map { TelegramNotice(it, text, "Ver pedido", "$base/panel/ventas/$orderId") }
            }
            AuditActions.PAYMENT_REPORTED -> {
                val paymentId = entityId ?: return emptyList()
                val p = jdbc.query(
                    """
                    select o.order_number, o.customer_name_snapshot, p.amount, p.method_snapshot, p.transaction_reference, o.business_id, o.id
                    from customer_payments p join orders o on o.id = p.order_id where p.id = ?
                    """.trimIndent(),
                    { rs, _ -> listOf(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4), rs.getString(5), rs.getObject(6, UUID::class.java), rs.getObject(7, UUID::class.java)) },
                    paymentId,
                ).firstOrNull() ?: return emptyList()
                val method = if (p[3] == "PLIN") "Plin" else "Yape"
                val text = "💸 Pago por revisar · ${p[0]}\n${p[1]} reportó ${soles(p[2] as BigDecimal)} por $method" +
                    (p[4]?.let { "\nCódigo de operación: $it" } ?: "") +
                    "\n\nRevisa tu $method y confírmalo solo si el dinero llegó."
                ownerChats(p[5] as UUID).map { TelegramNotice(it, text, "Revisar pago", "$base/panel/ventas/${p[6]}") }
            }
            AuditActions.USER_REGISTERED -> {
                val business = businessId?.let(::businessName) ?: return emptyList()
                adminChats().map { TelegramNotice(it, "🆕 Nuevo vendedor registrado: $business", "Ver negocios", "$base/admin/negocios") }
            }
            AuditActions.SAAS_TRIAL_STARTED -> {
                val business = businessId?.let(::businessName) ?: return emptyList()
                adminChats().map { TelegramNotice(it, "🎁 $business empezó su prueba gratis de 14 días.", "Ver negocios", "$base/admin/negocios") }
            }
            AuditActions.SAAS_PAYMENT_REPORTED -> {
                val paymentId = entityId ?: return emptyList()
                val p = jdbc.query(
                    """
                    select b.business_name, i.plan_name_snapshot, p.amount, p.method
                    from saas_payments p join saas_invoices i on i.id = p.invoice_id join businesses b on b.id = p.business_id where p.id = ?
                    """.trimIndent(),
                    { rs, _ -> listOf(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4)) },
                    paymentId,
                ).firstOrNull() ?: return emptyList()
                val method = if (p[3] == "PLIN") "Plin" else "Yape"
                val text = "💳 Pago de plan por revisar\n${p[0]} · ${p[1]}: ${soles(p[2] as BigDecimal)} por $method\n\nApruébalo solo cuando veas el dinero en tu cuenta."
                adminChats().map { TelegramNotice(it, text, "Revisar pago", "$base/admin/pagos") }
            }
            else -> emptyList()
        }
    }

    private fun ownerChats(businessId: UUID): List<Long> = jdbc.query(
        "select l.chat_id from telegram_links l join businesses b on b.owner_id = l.user_id where b.id = ?",
        { rs, _ -> rs.getLong(1) }, businessId,
    )

    private fun adminChats(): List<Long> = jdbc.query(
        "select l.chat_id from telegram_links l join users u on u.id = l.user_id where u.platform_role = 'SUPER_ADMIN' and u.status = 'ACTIVE'",
        { rs, _ -> rs.getLong(1) },
    )

    private fun businessName(id: UUID): String? =
        jdbc.query("select business_name from businesses where id = ?", { rs, _ -> rs.getString(1) }, id).firstOrNull()

    private fun soles(amount: BigDecimal) = "S/ " + DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(amount)

    companion object {
        private val WATCHED = setOf(
            AuditActions.ORDER_CREATED, AuditActions.RENEWAL_ORDER_CREATED, AuditActions.PAYMENT_REPORTED,
            AuditActions.USER_REGISTERED, AuditActions.SAAS_TRIAL_STARTED, AuditActions.SAAS_PAYMENT_REPORTED,
        )
    }
}
