package com.nuvyra.support

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * API Pwned Passwords simulada (nunca se sale a internet). Responde el rango de cualquier prefijo con
 * relleno y, para [BREACHED], la línea con su sufijo y cuántas veces apareció.
 */
object FakePwnedPasswords {
    const val BREACHED = "contrasena-filtrada-2024"
    /** Ruta y valor de `Add-Padding` de cada consulta, para comprobar qué viaja. */
    val requests = ConcurrentLinkedDeque<Pair<String, String?>>()

    private val breachedHash = HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-1").digest(BREACHED.toByteArray()))

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/range/") { ex ->
            val prefix = ex.requestURI.path.substringAfterLast('/')
            requests.add(ex.requestURI.path to ex.requestHeaders.getFirst("Add-Padding"))
            val lines = buildList {
                add("0018A45C4D1DEF81644B54AB7F969B88D65:3")
                add("00D4F6E8FA6EECAD2A3AA415EEC418D38EC:0") // relleno
                if (breachedHash.startsWith(prefix)) add("${breachedHash.drop(5)}:1234")
            }
            val bytes = lines.joinToString("\r\n").toByteArray()
            ex.sendResponseHeaders(200, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"
}
