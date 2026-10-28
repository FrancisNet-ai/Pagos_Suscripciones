package com.nuvyra.media

import com.nuvyra.common.config.AppProperties
import com.nuvyra.common.persistence.UuidV7
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Almacenamiento local de archivos con nombres aleatorios no adivinables, en dos zonas:
 * - pública: logos, portadas, imágenes de productos y QR (servidas por [MediaController]);
 * - privada: comprobantes de pago, solo descargables por el vendedor autenticado.
 * Se puede sustituir por almacenamiento de objetos (S3 compatible) manteniendo esta API.
 */
@Service
class MediaStorage(private val properties: AppProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val publicRoot: Path = createRoot(properties.storage.localDir)
    private val privateRoot: Path = createRoot(properties.storage.privateDir)

    /**
     * Valida y guarda una imagen pública con una versión reducida para listados y móviles
     * (ver [Thumbnails]). Devuelve la URL pública relativa del original.
     */
    fun storeImage(file: MultipartFile): String {
        val bytes = file.bytes
        val key = store(publicRoot, bytes, properties.storage.maxImageBytes)
        Thumbnails.create(bytes, THUMB_WIDTH)?.let { small ->
            val variant = variantKey(key, small.type)
            Files.write(publicRoot.resolve(variant), small.bytes, StandardOpenOption.CREATE_NEW)
            registerRollbackCleanup(publicRoot, variant)
        }
        return PUBLIC_PREFIX + key
    }

    /** Valida y guarda un comprobante privado. Devuelve su clave interna. */
    fun storeReceipt(bytes: ByteArray): String = store(privateRoot, bytes, properties.storage.maxReceiptBytes)

    /** Elimina el archivo anterior solo cuando la transacción que lo reemplaza se confirma. */
    fun deleteAfterCommit(url: String?) {
        val key = keyFromUrl(url) ?: return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = delete(publicRoot, key)
            })
        } else {
            delete(publicRoot, key)
        }
    }

    fun resolve(key: String): Path? = resolveIn(publicRoot, key)

    /** Versión reducida de una imagen pública, si existe (las WebP y las antiguas no la tienen). */
    fun resolveSmall(key: String): Path? {
        if (!KEY_PATTERN.matches(key)) return null
        return ImageType.entries.firstNotNullOfOrNull { type ->
            publicRoot.resolve(variantKey(key, type)).normalize().takeIf { it.startsWith(publicRoot) && Files.isRegularFile(it) }
        }
    }

    fun resolveReceipt(key: String): Path? = resolveIn(privateRoot, key)

    private fun store(root: Path, bytes: ByteArray, maxBytes: Long): String {
        val type = ImageValidator.validate(bytes, maxBytes, properties.storage.maxImageDimension)
        val key = "${UuidV7.generate()}.${type.extension}"
        Files.write(root.resolve(key), bytes, StandardOpenOption.CREATE_NEW)
        registerRollbackCleanup(root, key)
        return key
    }

    /** Si la transacción que usa el archivo falla, no queda huérfano. */
    private fun registerRollbackCleanup(root: Path, key: String) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    if (status == TransactionSynchronization.STATUS_ROLLED_BACK) delete(root, key)
                }
            })
        }
    }

    private fun resolveIn(root: Path, key: String): Path? {
        if (!KEY_PATTERN.matches(key)) return null
        val path = root.resolve(key).normalize()
        return path.takeIf { it.startsWith(root) && Files.isRegularFile(it) }
    }

    private fun delete(root: Path, key: String) {
        try {
            resolveIn(root, key)?.let(Files::deleteIfExists)
            if (root == publicRoot) ImageType.entries.forEach { Files.deleteIfExists(root.resolve(variantKey(key, it))) }
        } catch (ex: Exception) {
            log.warn("No se pudo eliminar el archivo {}", key, ex)
        }
    }

    private fun createRoot(dir: String): Path =
        Path.of(dir).toAbsolutePath().normalize().also { Files.createDirectories(it) }

    companion object {
        const val PUBLIC_PREFIX = "/api/v1/public/media/"
        const val THUMB_WIDTH = 640

        /** `0192…-ab.png` → `0192…-ab-sm.jpg`: nombre derivado, nunca recibido del cliente. */
        fun variantKey(key: String, type: ImageType) = key.substringBeforeLast('.') + "-sm." + type.extension
        val KEY_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)$")

        fun keyFromUrl(url: String?): String? = url?.removePrefix(PUBLIC_PREFIX)?.takeIf { KEY_PATTERN.matches(it) }
    }
}
