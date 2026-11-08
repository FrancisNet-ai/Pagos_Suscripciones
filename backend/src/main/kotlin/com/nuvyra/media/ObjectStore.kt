package com.nuvyra.media

import com.nuvyra.common.config.AppProperties
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Duration

/** Zona pública (imágenes de tiendas) o privada (comprobantes de pago). Ninguna se expone directamente. */
enum class StoreZone { PUBLIC, PRIVATE }

/**
 * Dónde viven los archivos. Las claves las genera [MediaStorage] (UUID aleatorio + extensión) y se validan
 * antes de llegar aquí: nunca vienen del cliente.
 */
interface ObjectStore {
    fun put(zone: StoreZone, key: String, bytes: ByteArray, contentType: String)

    /** `null` si no existe. */
    fun get(zone: StoreZone, key: String): ByteArray?

    fun delete(zone: StoreZone, key: String)
}

/** Disco local: desarrollo, pruebas y despliegue con Docker Compose (volúmenes `media` y `private`). */
class LocalObjectStore(publicDir: String, privateDir: String) : ObjectStore {
    private val roots = mapOf(StoreZone.PUBLIC to createRoot(publicDir), StoreZone.PRIVATE to createRoot(privateDir))

    override fun put(zone: StoreZone, key: String, bytes: ByteArray, contentType: String) {
        Files.write(path(zone, key), bytes, StandardOpenOption.CREATE_NEW)
    }

    override fun get(zone: StoreZone, key: String): ByteArray? = path(zone, key).takeIf(Files::isRegularFile)?.let(Files::readAllBytes)

    override fun delete(zone: StoreZone, key: String) {
        Files.deleteIfExists(path(zone, key))
    }

    private fun path(zone: StoreZone, key: String): Path {
        val root = roots.getValue(zone)
        return root.resolve(key).normalize().also { require(it.startsWith(root)) { "Clave fuera de la carpeta" } }
    }

    private fun createRoot(dir: String): Path = Path.of(dir).toAbsolutePath().normalize().also { Files.createDirectories(it) }
}

/**
 * Supabase Storage por su API HTTP, con la clave secreta del proyecto (solo en el servidor). Los dos buckets son
 * privados: las imágenes públicas las sigue sirviendo [MediaController] y los comprobantes, el panel autenticado.
 * Los buckets se crean solos la primera vez.
 */
class SupabaseObjectStore(
    baseUrl: String,
    private val secretKey: String,
    private val buckets: Map<StoreZone, String>,
    timeout: Duration,
) : ObjectStore {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client = RestClient.builder()
        .baseUrl(baseUrl.trimEnd('/') + "/storage/v1")
        .requestFactory(SimpleClientHttpRequestFactory().apply { setConnectTimeout(timeout); setReadTimeout(timeout) })
        .defaultHeader("apikey", secretKey)
        // Las claves nuevas (`sb_secret_…`) no son JWT y van solo en `apikey`; la antigua `service_role` (JWT) también
        // se envía como Bearer.
        .defaultHeaders { if (!secretKey.startsWith("sb_")) it.setBearerAuth(secretKey) }
        .build()

    @Volatile
    private var bucketsReady = false

    override fun put(zone: StoreZone, key: String, bytes: ByteArray, contentType: String) {
        ensureBuckets()
        client.post().uri("/object/{bucket}/{key}", buckets.getValue(zone), key)
            .contentType(MediaType.parseMediaType(contentType))
            .header("x-upsert", "false")
            .body(bytes)
            .retrieve()
            .toBodilessEntity()
    }

    override fun get(zone: StoreZone, key: String): ByteArray? = try {
        client.get().uri("/object/authenticated/{bucket}/{key}", buckets.getValue(zone), key).retrieve().body(ByteArray::class.java)
    } catch (ex: RestClientResponseException) {
        // Supabase responde 400 o 404 cuando el objeto no existe.
        if (ex.statusCode.value() in setOf(400, 404)) null else throw ex
    }

    override fun delete(zone: StoreZone, key: String) {
        client.delete().uri("/object/{bucket}/{key}", buckets.getValue(zone), key).retrieve()
            .onStatus(HttpStatusCode::is4xxClientError) { _, _ -> }
            .toBodilessEntity()
    }

    private fun ensureBuckets() {
        if (bucketsReady) return
        synchronized(this) {
            if (bucketsReady) return
            buckets.values.forEach { bucket ->
                try {
                    client.post().uri("/bucket").contentType(MediaType.APPLICATION_JSON)
                        .body(mapOf("id" to bucket, "name" to bucket, "public" to false))
                        .retrieve().toBodilessEntity()
                    log.info("Bucket {} creado en Supabase Storage", bucket)
                } catch (ex: RestClientResponseException) {
                    // 409 (o 400 «already exists»): ya existía.
                    if (ex.statusCode.value() !in setOf(400, 409)) throw ex
                }
            }
            bucketsReady = true
        }
    }
}

@Configuration(proxyBeanMethods = false)
class ObjectStoreConfig {
    @Bean
    fun objectStore(properties: AppProperties): ObjectStore {
        val storage = properties.storage
        return when (storage.type.lowercase()) {
            "local" -> LocalObjectStore(storage.localDir, storage.privateDir)
            "supabase" -> {
                require(storage.supabaseUrl.isNotBlank() && storage.supabaseSecretKey.isNotBlank()) {
                    "APP_STORAGE_SUPABASE_URL y APP_STORAGE_SUPABASE_SECRET_KEY son obligatorios con APP_STORAGE_TYPE=supabase"
                }
                SupabaseObjectStore(
                    storage.supabaseUrl, storage.supabaseSecretKey,
                    mapOf(StoreZone.PUBLIC to storage.publicBucket, StoreZone.PRIVATE to storage.privateBucket),
                    storage.requestTimeout,
                )
            }
            else -> error("APP_STORAGE_TYPE debe ser local o supabase")
        }
    }
}
