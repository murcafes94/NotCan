package com.notcan.app.ai

import java.net.URI

enum class TuNotSourceAuthority {
    OFFICIAL_CHURCH,
    CATHOLIC_REFERENCE,
    CATHOLIC_ACADEMIC
}

data class TuNotWebSource(
    val domain: String,
    val label: String,
    val authority: TuNotSourceAuthority,
    val priority: Int,
    val topics: Set<String> = emptySet()
)

/**
 * Lista blanca de fuentes web para TuNot.
 *
 * La intención es evitar que el tutor mezcle respuestas doctrinales con resultados
 * cristianos generales, confesionales no católicos o páginas de autoridad incierta.
 * El ejecutor de búsqueda web debe filtrar cualquier URL con [isAllowedUrl] antes de
 * incorporar contenido al contexto del modelo.
 */
object TuNotCatholicSourcePolicy {

    val sources: List<TuNotWebSource> = listOf(
        TuNotWebSource(
            domain = "vatican.va",
            label = "Santa Sede",
            authority = TuNotSourceAuthority.OFFICIAL_CHURCH,
            priority = 100,
            topics = setOf("magisterio", "concilios", "papas", "doctrina", "liturgia", "derecho canonico")
        ),
        TuNotWebSource(
            domain = "papalencyclicals.net",
            label = "Papal Encyclicals Online",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 90,
            topics = setOf("magisterio", "enciclicas", "concilios", "papas")
        ),
        TuNotWebSource(
            domain = "canonlaw.ninja",
            label = "Canon Law Ninja",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 88,
            topics = setOf("derecho canonico", "cic", "cce", "liturgia")
        ),
        TuNotWebSource(
            domain = "newadvent.org",
            label = "New Advent",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 84,
            topics = setOf("patristica", "tomismo", "historia", "teologia", "enciclopedia catolica")
        ),
        TuNotWebSource(
            domain = "corpusthomisticum.org",
            label = "Corpus Thomisticum",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 84,
            topics = setOf("tomas de aquino", "escolastica", "filosofia", "teologia")
        ),
        TuNotWebSource(
            domain = "documentacatholicaomnia.eu",
            label = "Documenta Catholica Omnia",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 82,
            topics = setOf("patristica", "magisterio", "concilios", "historia")
        ),
        TuNotWebSource(
            domain = "comillas.edu",
            label = "Universidad Pontificia Comillas",
            authority = TuNotSourceAuthority.CATHOLIC_ACADEMIC,
            priority = 78,
            topics = setOf("teologia", "filosofia", "biblia", "bibliografia")
        ),
        TuNotWebSource(
            domain = "javeriana.edu.co",
            label = "Pontificia Universidad Javeriana",
            authority = TuNotSourceAuthority.CATHOLIC_ACADEMIC,
            priority = 77,
            topics = setOf("teologia", "filosofia", "biblia", "bibliografia")
        ),
        TuNotWebSource(
            domain = "javeriana.libguides.com",
            label = "Guías de Teología Javeriana",
            authority = TuNotSourceAuthority.CATHOLIC_ACADEMIC,
            priority = 76,
            topics = setOf("teologia", "bibliografia", "recursos academicos")
        ),
        TuNotWebSource(
            domain = "bibliotecas.uc.cl",
            label = "Pontificia Universidad Católica de Chile",
            authority = TuNotSourceAuthority.CATHOLIC_ACADEMIC,
            priority = 76,
            topics = setOf("teologia", "filosofia", "biblia", "bibliografia")
        ),
        TuNotWebSource(
            domain = "uc.cl",
            label = "Pontificia Universidad Católica de Chile",
            authority = TuNotSourceAuthority.CATHOLIC_ACADEMIC,
            priority = 75,
            topics = setOf("teologia", "filosofia", "biblia")
        ),
        TuNotWebSource(
            domain = "unav.edu",
            label = "Universidad de Navarra",
            authority = TuNotSourceAuthority.CATHOLIC_ACADEMIC,
            priority = 74,
            topics = setOf("teologia", "filosofia", "derecho canonico", "biblia", "bibliografia")
        )
    ).sortedByDescending { it.priority }

    private val allowedDomains = sources.map { it.domain.lowercase() }.toSet()

    fun isAllowedUrl(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) return false
        return allowedDomains.any { domain -> host == domain || host.endsWith(".$domain") }
    }

    fun sourceForUrl(url: String): TuNotWebSource? {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) return null
        return sources.firstOrNull { source -> host == source.domain || host.endsWith(".${source.domain}") }
    }

    fun promptPolicy(): String = buildString {
        appendLine("POLÍTICA CATÓLICA DE FUENTES WEB DE TUNOT")
        appendLine("TuNot es un tutor académico católico. Para consultas doctrinales no debe presentar como doctrina católica una opinión cristiana general, ecuménica o de otra confesión.")
        appendLine("Si existe búsqueda web, utiliza únicamente dominios de la lista blanca de NotCan y respeta el siguiente orden de autoridad:")
        appendLine("1. Santa Sede / documentos oficiales de la Iglesia.")
        appendLine("2. Fuentes católicas de referencia y textos primarios.")
        appendLine("3. Universidades e instituciones académicas católicas.")
        appendLine("En una pregunta del tipo 'qué enseña la Iglesia', una fuente académica nunca debe contradecir ni sustituir una fuente oficial.")
        appendLine("Distingue doctrina, disciplina vigente, opinión teológica e interpretación académica cuando corresponda.")
        appendLine("No atribuyas al Magisterio una conclusión que solo aparezca en una fuente secundaria.")
        appendLine("No uses como fundamento doctrinal páginas protestantes, evangélicas, ortodoxas, ecuménicas generales, wikis, foros, redes sociales o sitios sin autoridad identificable.")
        appendLine("Dominios autorizados, de mayor a menor prioridad:")
        sources.forEach { source ->
            appendLine("- ${source.domain} — ${source.label} — ${source.authority.name}")
        }
    }
}
