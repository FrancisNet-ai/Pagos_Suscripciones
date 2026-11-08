package com.nuvyra.media

import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.persistence.UuidV7
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile

/** Archivo leído del almacén, con su tipo deducido de la clave. */
class StoredFile(val bytes: ByteArray, val type: ImageType)

/**
 * Archivos con nombres aleatorios no adivinables, en dos zonas:
 * - pública: logos, portadas, imágenes de productos y QR (servidas por [MediaController]);
 * - privada: comprobantes de pago, solo descargables por el vendedor autenticado.
 * Dónde se guardan lo decide [ObjectStore]: disco local o Supabase Storage.
 */
@Service
class MediaStorage(private val properties: AppProperties, private val store: ObjectStore) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Valida y guarda una imagen pública con una versión reducida para listados y móviles
     * (ver [Thumbnails]). Devuelve la URL pública relativa del original.
     */
    fun storeImage(file: MultipartFile): String {
        val bytes = file.bytes
        val key = store(StoreZone.PUBLIC, bytes, properties.storage.maxImageBytes)
        Thumbnails.create(bytes, THUMB_WIDTH)?.let { small ->
            val variant = variantKey(key, small.type)
            store.put(StoreZone.PUBLIC, variant, small.bytes, small.type.contentType)
            registerRollbackCleanup(StoreZone.PUBLIC, variant)
        }
        return PUBLIC_PREFIX + key
    }

    /** Valida y guarda un comprobante privado. Devuelve su clave interna. */
    fun storeReceipt(bytes: ByteArray): String = store(StoreZone.PRIVATE, bytes, properties.storage.maxReceiptBytes)

    /** Elimina el archivo anterior solo cuando la transacción que lo reemplaza se confirma. */
    fun deleteAfterCommit(url: String?) {
        val key = keyFromUrl(url) ?: return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = deleteImage(key)
            })
        } else {
            deleteImage(key)
        }
    }

    fun resolve(key: String): StoredFile? = read(StoreZone.PUBLIC, key)

    /** Versión reducida de una imagen pública, si existe (las WebP y las antiguas no la tienen). */
    fun resolveSmall(key: String): StoredFile? {
        if (!KEY_PATTERN.matches(key) || key.endsWith(".webp")) return null
        return SMALL_TYPES.firstNotNullOfOrNull { type -> read(StoreZone.PUBLIC, variantKey(key, type), checkKey = false) }
    }

    fun resolveReceipt(key: String): StoredFile? = read(StoreZone.PRIVATE, key)

    private fun store(zone: StoreZone, bytes: ByteArray, maxBytes: Long): String {
        val type = ImageValidator.validate(bytes, maxBytes, properties.storage.maxImageDimension)
        val key = "${UuidV7.generate()}.${type.extension}"
        store.put(zone, key, bytes, type.contentType)
        registerRollbackCleanup(zone, key)
        return key
    }

    private fun read(zone: StoreZone, key: String, checkKey: Boolean = true): StoredFile? {
        if (checkKey && !KEY_PATTERN.matches(key)) return null
        val type = ImageType.fromExtension(key.substringAfterLast('.')) ?: return null
        return store.get(zone, key)?.let { StoredFile(it, type) }
    }

    /** Si la transacción que usa el archivo falla, no queda huérfano. */
    private fun registerRollbackCleanup(zone: StoreZone, key: String) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) delete(zone, key)
                }
            })
        }
    }

    private fun deleteImage(key: String) {
        delete(StoreZone.PUBLIC, key)
        SMALL_TYPES.forEach { delete(StoreZone.PUBLIC, variantKey(key, it)) }
    }

    private fun delete(zone: StoreZone, key: String) {
        try {
            store.delete(zone, key)
        } catch (ex: Exception) {
            log.warn("No se pudo eliminar el archivo {}", key, ex)
        }
    }

    companion object {
        const val PUBLIC_PREFIX = "/api/v1/public/media/"
        const val THUMB_WIDTH = 640

        /** Las versiones reducidas se guardan en JPEG (fotos) o PNG (con transparencia). */
        private val SMALL_TYPES = listOf(ImageType.JPEG, ImageType.PNG)

        /** `0192…-ab.png` → `0192…-ab-sm.jpg`: nombre derivado, nunca recibido del cliente. */
        fun variantKey(key: String, type: ImageType) = key.substringBeforeLast('.') + "-sm." + type.extension
        val KEY_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)$")

        fun keyFromUrl(url: String?): String? = url?.removePrefix(PUBLIC_PREFIX)?.takeIf { KEY_PATTERN.matches(it) }
    }
}
