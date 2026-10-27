package com.platform.media

import com.platform.common.config.AppProperties
import com.platform.common.persistence.UuidV7
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Almacenamiento local de imágenes públicas con nombres aleatorios no adivinables.
 * Se puede sustituir por un almacenamiento de objetos (S3 compatible) manteniendo esta API.
 */
@Service
class MediaStorage(private val properties: AppProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val root: Path = Path.of(properties.storage.localDir).toAbsolutePath().normalize()

    init {
        Files.createDirectories(root)
    }

    /** Valida y guarda la imagen. Devuelve la URL pública relativa. */
    fun storeImage(file: MultipartFile): String {
        val bytes = file.bytes
        val type = ImageValidator.validate(bytes, properties.storage.maxImageBytes, properties.storage.maxImageDimension)
        val key = "${UuidV7.generate()}.${type.extension}"
        Files.write(root.resolve(key), bytes, StandardOpenOption.CREATE_NEW)
        // Si la transacción que usa la imagen falla, el archivo no queda huérfano.
        runAfterRollback { delete(key) }
        return PUBLIC_PREFIX + key
    }

    /** Elimina el archivo anterior solo cuando la transacción que lo reemplaza se confirma. */
    fun deleteAfterCommit(url: String?) {
        val key = keyFromUrl(url) ?: return
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() = delete(key)
            })
        } else {
            delete(key)
        }
    }

    fun resolve(key: String): Path? {
        if (!KEY_PATTERN.matches(key)) return null
        val path = root.resolve(key).normalize()
        return path.takeIf { it.startsWith(root) && Files.isRegularFile(it) }
    }

    private fun delete(key: String) {
        try {
            resolve(key)?.let(Files::deleteIfExists)
        } catch (ex: Exception) {
            log.warn("No se pudo eliminar el archivo {}", key, ex)
        }
    }

    private fun runAfterRollback(action: () -> Unit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) action()
            }
        })
    }

    companion object {
        const val PUBLIC_PREFIX = "/api/v1/public/media/"
        val KEY_PATTERN = Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg|webp)$")

        fun keyFromUrl(url: String?): String? = url?.removePrefix(PUBLIC_PREFIX)?.takeIf { KEY_PATTERN.matches(it) }
    }
}
