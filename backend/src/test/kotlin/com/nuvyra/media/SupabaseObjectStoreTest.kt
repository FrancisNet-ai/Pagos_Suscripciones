package com.nuvyra.media

import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/** Supabase Storage simulado: comprueba rutas, cabeceras y la creación de buckets. */
class SupabaseObjectStoreTest {
    private val objects = ConcurrentHashMap<String, ByteArray>()
    private val buckets = ConcurrentHashMap.newKeySet<String>()
    private val authHeaders = CopyOnWriteArrayList<String?>()
    private val apiKeys = CopyOnWriteArrayList<String?>()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/storage/v1/") { ex ->
            authHeaders.add(ex.requestHeaders.getFirst("Authorization"))
            apiKeys.add(ex.requestHeaders.getFirst("apikey"))
            val path = ex.requestURI.path.removePrefix("/storage/v1")
            val body = ex.requestBody.readBytes()
            val (status, response) = when {
                ex.requestMethod == "POST" && path == "/bucket" -> {
                    val id = Regex("\"id\":\"([^\"]+)\"").find(String(body))!!.groupValues[1]
                    if (buckets.add(id)) 200 to "{}".toByteArray() else 409 to "{\"error\":\"Duplicate\"}".toByteArray()
                }
                ex.requestMethod == "POST" && path.startsWith("/object/") -> {
                    val key = path.removePrefix("/object/")
                    if (key.substringBefore('/') !in buckets) 400 to "{\"error\":\"Bucket not found\"}".toByteArray()
                    else { objects[key] = body; 200 to "{}".toByteArray() }
                }
                ex.requestMethod == "GET" && path.startsWith("/object/authenticated/") ->
                    objects[path.removePrefix("/object/authenticated/")]?.let { 200 to it } ?: (400 to "{\"error\":\"not_found\"}".toByteArray())
                ex.requestMethod == "DELETE" && path.startsWith("/object/") ->
                    if (objects.remove(path.removePrefix("/object/")) != null) 200 to "{}".toByteArray() else 400 to "{}".toByteArray()
                else -> 404 to ByteArray(0)
            }
            ex.sendResponseHeaders(status, response.size.toLong().takeIf { it > 0 } ?: -1)
            ex.responseBody.use { if (response.isNotEmpty()) it.write(response) }
        }
        start()
    }

    private val store = SupabaseObjectStore(
        "http://127.0.0.1:${server.address.port}", "clave-secreta-de-prueba",
        mapOf(StoreZone.PUBLIC to "nuvyra-media", StoreZone.PRIVATE to "nuvyra-private"), Duration.ofSeconds(5),
    )

    @AfterEach
    fun stop() = server.stop(0)

    @Test
    fun `crea los buckets una vez y guarda, lee y borra por zona`() {
        val bytes = byteArrayOf(1, 2, 3)
        store.put(StoreZone.PRIVATE, "a.png", bytes, "image/png")
        store.put(StoreZone.PUBLIC, "b.jpg", bytes, "image/jpeg")

        assertThat(buckets).containsExactlyInAnyOrder("nuvyra-media", "nuvyra-private")
        assertThat(objects.keys).containsExactlyInAnyOrder("nuvyra-private/a.png", "nuvyra-media/b.jpg")
        assertThat(store.get(StoreZone.PRIVATE, "a.png")).isEqualTo(bytes)
        // Las zonas no se mezclan: un comprobante no se puede leer como imagen pública.
        assertThat(store.get(StoreZone.PUBLIC, "a.png")).isNull()

        store.delete(StoreZone.PRIVATE, "a.png")
        store.delete(StoreZone.PRIVATE, "no-existe.png")
        assertThat(store.get(StoreZone.PRIVATE, "a.png")).isNull()
        assertThat(authHeaders).allMatch { it == "Bearer clave-secreta-de-prueba" }
        assertThat(apiKeys).allMatch { it == "clave-secreta-de-prueba" }
    }

    @Test
    fun `si los buckets ya existian sigue funcionando`() {
        buckets.addAll(listOf("nuvyra-media", "nuvyra-private"))
        store.put(StoreZone.PUBLIC, "c.png", byteArrayOf(9), "image/png")
        assertThat(store.get(StoreZone.PUBLIC, "c.png")).containsExactly(9)
    }

    @Test
    fun `una clave secreta nueva va solo en la cabecera apikey`() {
        val modern = SupabaseObjectStore(
            "http://127.0.0.1:${server.address.port}", "sb_secret_prueba",
            mapOf(StoreZone.PUBLIC to "nuvyra-media", StoreZone.PRIVATE to "nuvyra-private"), Duration.ofSeconds(5),
        )
        modern.put(StoreZone.PUBLIC, "d.png", byteArrayOf(1), "image/png")
        assertThat(apiKeys).allMatch { it == "sb_secret_prueba" }
        assertThat(authHeaders).allMatch { it == null }
    }
}
