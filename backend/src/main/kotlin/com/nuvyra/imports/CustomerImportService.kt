package com.nuvyra.imports

import com.nuvyra.businesses.BusinessService
import com.nuvyra.common.audit.AuditActions
import com.nuvyra.common.audit.AuditService
import com.nuvyra.common.error.ApiException
import com.nuvyra.common.ratelimit.RateLimiter
import com.nuvyra.common.validation.PeruPhone
import com.nuvyra.customers.Customer
import com.nuvyra.customers.CustomerRepository
import com.nuvyra.customers.CustomerSource
import com.nuvyra.products.Product
import com.nuvyra.products.ProductRepository
import com.nuvyra.saas.EntitlementService
import com.nuvyra.subscriptions.ManualStatus
import com.nuvyra.subscriptions.ManualSubscriptionRequest
import com.nuvyra.subscriptions.SubscriptionService
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Importación masiva de clientes que el vendedor ya tenía. Dos pasos: el archivo se analiza y se
 * guarda una vista previa; al confirmar se aplican exactamente esas filas. Las suscripciones se
 * registran igual que una alta manual (no cuentan como ventas nuevas y respetan los cupos).
 */
@Service
class CustomerImportService(
    private val imports: CustomerImportRepository,
    private val customers: CustomerRepository,
    private val products: ProductRepository,
    private val businesses: BusinessService,
    private val entitlements: EntitlementService,
    private val writer: ImportRowWriter,
    private val rateLimiter: RateLimiter,
    private val audit: AuditService,
    private val objectMapper: ObjectMapper,
    private val clock: Clock,
) {
    @Transactional
    fun preview(businessId: UUID, actorId: UUID, fileName: String, bytes: ByteArray): ImportResponse {
        rateLimiter.check("import:$businessId", limit = 30, window = Duration.ofHours(1))
        if (bytes.size > MAX_BYTES) throw ApiException.badRequest("FILE_TOO_LARGE", "El archivo supera los 2 MB.")
        val raw = SpreadsheetReader.read(fileName, bytes)
        val zone = ZoneId.of(businesses.get(businessId).timeZone)
        val today = clock.instant().atZone(zone).toLocalDate()
        val catalog = products.findByBusinessId(businessId)
        val seen = mutableMapOf<String, Int>()

        // Límite de clientes del plan: los nuevos que no caben se marcan como error.
        val limit = entitlements.access(businessId).plan?.customerLimit
        var room = limit?.let { maxOf(0L, it - entitlements.usage(businessId).customers) }

        val rows = raw.map { r ->
            val row = parseRow(r, businessId, catalog, today, seen)
            if (row.action == RowAction.CREATE && room != null) {
                if (room!! <= 0) row.copy(action = RowAction.ERROR, errors = listOf("Supera el límite de clientes de tu plan")) else row.also { room = room!! - 1 }
            } else {
                row
            }
        }
        val saved = imports.save(
            CustomerImport(
                businessId = businessId, fileName = fileName.take(255), rows = objectMapper.writeValueAsString(rows),
                totalRows = rows.size, validRows = rows.count { it.action != RowAction.ERROR }, createdBy = actorId,
                expiresAt = clock.instant().plus(Duration.ofHours(24)),
            ),
        )
        return toResponse(saved, rows)
    }

    @Transactional(readOnly = true)
    fun get(businessId: UUID, importId: UUID): ImportResponse {
        val i = imports.findByIdAndBusinessId(importId, businessId) ?: throw ApiException.notFound("La importación no existe.")
        return toResponse(i, rowsOf(i))
    }

    /** Aplica la vista previa. Cada fila va en su propia transacción: un cupo agotado no deshace el resto. */
    @Transactional
    fun commit(businessId: UUID, actorId: UUID, importId: UUID): ImportResponse {
        val i = imports.findByIdAndBusinessId(importId, businessId)?.let { imports.lockById(it.id) } ?: throw ApiException.notFound("La importación no existe.")
        if (i.status != ImportStatus.PREVIEW) throw ApiException.conflict("IMPORT_CLOSED", "Esta importación ya fue aplicada o descartada.")
        if (!clock.instant().isBefore(i.expiresAt)) throw ApiException.conflict("IMPORT_EXPIRED", "La vista previa venció. Sube el archivo de nuevo.")
        entitlements.requireActive(businessId)
        val zone = ZoneId.of(businesses.get(businessId).timeZone)
        val results = rowsOf(i).map { row ->
            if (row.action == RowAction.ERROR) {
                row.copy(outcome = RowOutcome.SKIPPED)
            } else {
                try {
                    val outcome = writer.write(businessId, actorId, zone, row)
                    row.copy(outcome = outcome)
                } catch (ex: ApiException) {
                    row.copy(outcome = RowOutcome.FAILED, outcomeMessage = ex.fieldErrors.firstOrNull()?.message ?: ex.message)
                }
            }
        }
        val summary = ImportResultSummary(
            created = results.count { it.outcome == RowOutcome.CREATED },
            subscriptionsAdded = results.count { it.outcome == RowOutcome.SUBSCRIPTION_ADDED || (it.outcome == RowOutcome.CREATED && it.hasSubscription) },
            unchanged = results.count { it.outcome == RowOutcome.UNCHANGED },
            failed = results.count { it.outcome == RowOutcome.FAILED },
            skipped = results.count { it.outcome == RowOutcome.SKIPPED },
        )
        i.rows = objectMapper.writeValueAsString(results)
        i.result = objectMapper.writeValueAsString(summary)
        i.status = ImportStatus.COMMITTED
        i.committedAt = clock.instant()
        audit.record(
            AuditActions.CUSTOMERS_IMPORTED, actorId = actorId, businessId = businessId, entityType = "CUSTOMER_IMPORT", entityId = i.id,
            metadata = mapOf("file" to i.fileName, "created" to summary.created, "subscriptions" to summary.subscriptionsAdded, "failed" to summary.failed),
        )
        return toResponse(i, results)
    }

    @Transactional
    fun discard(businessId: UUID, importId: UUID) {
        val i = imports.findByIdAndBusinessId(importId, businessId) ?: throw ApiException.notFound("La importación no existe.")
        if (i.status == ImportStatus.PREVIEW) i.status = ImportStatus.DISCARDED
    }

    private fun parseRow(r: RawRow, businessId: UUID, catalog: List<Product>, today: LocalDate, seen: MutableMap<String, Int>): ImportRow {
        val errors = mutableListOf<String>()
        fun bad(message: String): Nothing? {
            errors += message
            return null
        }
        val name = r.cells[ImportColumn.NAME]?.replace(Regex("\\s+"), " ")
        when {
            name == null -> errors += "Falta el nombre"
            name.length !in 2..120 -> errors += "El nombre debe tener entre 2 y 120 caracteres"
        }
        val phone = r.cells[ImportColumn.PHONE]?.let { PeruPhone.normalize(it) }
        if (phone == null) {
            errors += if (r.cells[ImportColumn.PHONE] == null) "Falta el celular" else "Celular no válido: «${r.cells[ImportColumn.PHONE]}»"
        } else {
            seen[phone]?.let { errors += "Celular repetido en el archivo (fila $it)" } ?: run { seen[phone] = r.rowNumber }
        }
        val notes = r.cells[ImportColumn.NOTES]?.take(1000)

        var product: Product? = null
        var start: LocalDate? = null
        var duration: Int? = null
        var price: BigDecimal? = null
        var status: ManualStatus? = null
        val productName = r.cells[ImportColumn.PRODUCT]
        val subscriptionColumns = listOf(ImportColumn.START, ImportColumn.DURATION, ImportColumn.END, ImportColumn.PRICE, ImportColumn.STATUS).any { it in r.cells }
        if (productName == null) {
            if (subscriptionColumns) errors += "Indica el producto para registrar la suscripción"
        } else {
            product = catalog.firstOrNull { it.name.trim().equals(productName, ignoreCase = true) }
            if (product == null) errors += "No existe el producto «$productName». Créalo primero o deja la columna vacía."
            start = r.cells[ImportColumn.START]?.let { parseDate(it) ?: bad("Fecha de inicio no válida: «$it» (usa dd/mm/aaaa)") }
            if (start == null && r.cells[ImportColumn.START] == null) errors += "Falta la fecha de inicio"
            val end = r.cells[ImportColumn.END]?.let { parseDate(it) ?: bad("Fecha de vencimiento no válida: «$it» (usa dd/mm/aaaa)") }
            duration = r.cells[ImportColumn.DURATION]?.let { it.toIntOrNull() ?: bad("Duración no válida: «$it»") }
                ?: if (start != null && end != null) ChronoUnit.DAYS.between(start, end).toInt() else product?.durationDays
            when {
                duration == null && product != null -> errors += "Indica la duración en días o la fecha de vencimiento"
                duration != null && duration !in 1..3650 -> errors += "La duración debe estar entre 1 y 3650 días"
            }
            price = r.cells[ImportColumn.PRICE]?.let { parsePrice(it) ?: bad("Precio no válido: «$it»") } ?: product?.price
            if (price != null && (price < BigDecimal.ZERO || price > BigDecimal("99999.99"))) errors += "Precio fuera de rango"
            if (start != null && start.isAfter(today.plusDays(30))) errors += "La fecha de inicio no puede estar tan adelantada"
            val expires = if (start != null && duration != null) start.plusDays(duration.toLong()) else null
            status = r.cells[ImportColumn.STATUS]?.let { parseStatus(it) ?: bad("Estado no válido: «$it» (activa, vencida o cancelada)") }
                ?: expires?.let { if (it.isAfter(today)) ManualStatus.ACTIVE else ManualStatus.EXPIRED }
            if (expires != null) {
                if (status == ManualStatus.ACTIVE && !expires.isAfter(today)) errors += "Con esas fechas la suscripción ya venció; indica estado «vencida»"
                if (status == ManualStatus.EXPIRED && expires.isAfter(today)) errors += "Con esas fechas la suscripción sigue vigente; indica estado «activa»"
            }
        }
        val action = when {
            errors.isNotEmpty() -> RowAction.ERROR
            customers.findByBusinessIdAndPhone(businessId, phone!!) != null -> RowAction.EXISTING
            else -> RowAction.CREATE
        }
        return ImportRow(
            rowNumber = r.rowNumber, fullName = name, phone = phone, notes = notes, productId = product?.id, productName = product?.name ?: productName,
            startDate = start, durationDays = duration, price = price?.setScale(2, RoundingMode.HALF_UP), status = status, action = action, errors = errors,
        )
    }

    private fun rowsOf(i: CustomerImport): List<ImportRow> = objectMapper.readValue(i.rows)

    private fun toResponse(i: CustomerImport, rows: List<ImportRow>) = ImportResponse(
        id = i.id, fileName = i.fileName, status = i.status, totalRows = i.totalRows,
        summary = ImportSummary(
            toCreate = rows.count { it.action == RowAction.CREATE },
            existing = rows.count { it.action == RowAction.EXISTING },
            withSubscription = rows.count { it.action != RowAction.ERROR && it.hasSubscription },
            errors = rows.count { it.action == RowAction.ERROR },
        ),
        result = i.result?.let { objectMapper.readValue<ImportResultSummary>(it) },
        expiresAt = i.expiresAt,
        rows = rows,
    )

    companion object {
        const val MAX_BYTES = 2 * 1024 * 1024
        private val DATE_FORMATS = listOf("d/M/uuuu", "d-M-uuuu", "uuuu-MM-dd", "d/M/uu").map { DateTimeFormatter.ofPattern(it) }

        fun parseDate(text: String): LocalDate? = DATE_FORMATS.firstNotNullOfOrNull { f ->
            try {
                LocalDate.parse(text.trim(), f)
            } catch (ex: DateTimeParseException) {
                null
            }
        }

        /** «15», «15.00», «S/ 15,50», «1,250.00». */
        fun parsePrice(text: String): BigDecimal? {
            var t = text.replace(Regex("(?i)s/|pen|\\s"), "")
            t = when {
                t.contains(',') && t.contains('.') -> t.replace(",", "")
                t.contains(',') -> t.replace(',', '.')
                else -> t
            }
            return t.toBigDecimalOrNull()
        }

        fun parseStatus(text: String): ManualStatus? = when (ImportColumn.normalize(text)) {
            "activa", "activo", "vigente", "active" -> ManualStatus.ACTIVE
            "vencida", "vencido", "expirada", "expired" -> ManualStatus.EXPIRED
            "cancelada", "cancelado", "cancelled" -> ManualStatus.CANCELLED
            else -> null
        }
    }
}

/** Escribe una fila en su propia transacción. */
@Component
class ImportRowWriter(
    private val customers: CustomerRepository,
    private val subscriptions: SubscriptionService,
    private val entitlements: EntitlementService,
    private val audit: AuditService,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun write(businessId: UUID, actorId: UUID, zone: ZoneId, row: ImportRow): RowOutcome {
        val phone = row.phone!!
        var created = false
        val customer = customers.findByBusinessIdAndPhone(businessId, phone) ?: run {
            entitlements.ensureCustomerCapacity(businessId, phone, storefront = false)
            created = true
            customers.save(Customer(businessId, row.fullName!!, phone, CustomerSource.IMPORT, row.notes)).also {
                audit.record(AuditActions.CUSTOMER_CREATED, actorId = actorId, businessId = businessId, entityType = "CUSTOMER", entityId = it.id, metadata = mapOf("source" to "IMPORT"))
            }
        }
        if (row.hasSubscription) {
            subscriptions.registerManual(
                businessId, actorId, customer,
                ManualSubscriptionRequest(row.productId, row.startDate, row.durationDays, row.price, row.status, null),
                zone,
            )
            return if (created) RowOutcome.CREATED else RowOutcome.SUBSCRIPTION_ADDED
        }
        return if (created) RowOutcome.CREATED else RowOutcome.UNCHANGED
    }
}
