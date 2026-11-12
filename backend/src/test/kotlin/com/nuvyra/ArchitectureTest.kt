package com.nuvyra

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Reglas de dependencias entre módulos (paquetes de primer nivel de `com.nuvyra`). Lee los `import` del código
 * fuente: no necesita base de datos ni Spring.
 *
 * - `common` es la base: no depende de ningún módulo.
 * - Los módulos de borde se pueden quitar sin romper el resto: nadie los importa.
 * - No se permiten dependencias circulares nuevas. Las que quedan están listadas y deben ir desapareciendo.
 */
class ArchitectureTest {
    private val root = File("src/main/kotlin/com/nuvyra")
    private val importRegex = Regex("""^import com\.nuvyra\.([a-z]+)\.""", RegexOption.MULTILINE)

    /** módulo → módulos que importa. */
    private val deps: Map<String, Set<String>> by lazy {
        root.listFiles()!!.filter { it.isDirectory }.associate { dir ->
            dir.name to dir.walkTopDown().filter { it.extension == "kt" }
                .flatMap { importRegex.findAll(it.readText()).map { m -> m.groupValues[1] } }
                .filter { it != dir.name }
                .toSortedSet()
        }
    }

    @Test
    fun `common no depende de ningun modulo`() {
        assertThat(deps.getValue("common")).isEmpty()
    }

    @Test
    fun `los modulos de borde no son usados por otros`() {
        val edge = setOf("admin", "imports", "reports", "staff", "supportaccess", "tasks", "telegram", "web")
        val offenders = deps.flatMap { (module, uses) -> uses.intersect(edge).map { "$module -> $it" } }
        assertThat(offenders).describedAs("Un módulo de borde debe poder quitarse sin romper el resto").isEmpty()
    }

    @Test
    fun `no hay dependencias circulares nuevas`() {
        // Nudo conocido del núcleo de ventas (dependencias circulares directas o en cadena). Se deshace con eventos
        // de dominio; al sacar un módulo del nudo, quitarlo también de aquí.
        val knownKnot = setOf("orders", "payments", "products", "renewals", "saas", "storefront", "subscriptions", "whatsapp")
        val knots = stronglyConnected().filter { it.size > 1 }
        val outside = knots.flatten().toSet() - knownKnot
        assertThat(outside).describedAs("Módulos nuevos dentro de una dependencia circular").isEmpty()
        assertThat(knots.size).describedAs("Debe haber como máximo un nudo").isLessThanOrEqualTo(1)
    }

    /** Componentes fuertemente conexos (Tarjan): cada grupo de más de un módulo es una dependencia circular. */
    private fun stronglyConnected(): List<Set<String>> {
        var counter = 0
        val index = mutableMapOf<String, Int>()
        val low = mutableMapOf<String, Int>()
        val stack = ArrayDeque<String>()
        val result = mutableListOf<Set<String>>()
        fun visit(v: String) {
            index[v] = counter; low[v] = counter; counter++
            stack.addLast(v)
            for (w in deps[v].orEmpty()) {
                if (w !in index) {
                    visit(w); low[v] = minOf(low.getValue(v), low.getValue(w))
                } else if (w in stack) {
                    low[v] = minOf(low.getValue(v), index.getValue(w))
                }
            }
            if (low[v] == index[v]) {
                val group = mutableSetOf<String>()
                do { val w = stack.removeLast(); group += w } while (w != v)
                result += group
            }
        }
        deps.keys.forEach { if (it !in index) visit(it) }
        return result
    }
}
