package com.notcan.app.ai

import java.net.URI

enum class TuNotSourceAuthority {
    OFFICIAL_CHURCH,
    CATHOLIC_REFERENCE,
    CATHOLIC_ACADEMIC,
    CATHOLIC_FORMATION,
    CATHOLIC_MEDIA
}

data class TuNotWebSource(
    val domain: String,
    val label: String,
    val authority: TuNotSourceAuthority,
    val priority: Int,
    val topics: Set<String> = emptySet(),
    val preferredLanguage: String = "es"
)

/**
 * Lista blanca de fuentes web para TuNot.
 *
 * El objetivo es mantener una identidad doctrinal católica y evitar que resultados
 * cristianos generales o de autoridad incierta entren al contexto del tutor como si
 * fueran equivalentes a fuentes de la Iglesia.
 *
 * El ejecutor web debe aplicar [isAllowedUrl] antes de entregar contenido al modelo.
 * Aunque una fuente esté originalmente en inglés, TuNot debe sintetizarla y responder
 * siempre en español salvo que el usuario pida explícitamente el original.
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
            topics = setOf("magisterio", "enciclicas", "concilios", "papas"),
            preferredLanguage = "en"
        ),
        TuNotWebSource(
            domain = "canonlaw.ninja",
            label = "Canon Law Ninja",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 88,
            topics = setOf("derecho canonico", "cic", "cce", "liturgia"),
            preferredLanguage = "en"
        ),
        TuNotWebSource(
            domain = "magisterium.com",
            label = "Magisterium AI",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 87,
            topics = setOf("magisterio", "catecismo", "teologia", "patristica", "doctrina")
        ),
        TuNotWebSource(
            domain = "newadvent.org",
            label = "New Advent",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 84,
            topics = setOf("patristica", "tomismo", "historia", "teologia", "enciclopedia catolica"),
            preferredLanguage = "en"
        ),
        TuNotWebSource(
            domain = "corpusthomisticum.org",
            label = "Corpus Thomisticum",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 84,
            topics = setOf("tomas de aquino", "escolastica", "filosofia", "teologia"),
            preferredLanguage = "la"
        ),
        TuNotWebSource(
            domain = "documentacatholicaomnia.eu",
            label = "Documenta Catholica Omnia",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 82,
            topics = setOf("patristica", "magisterio", "concilios", "historia"),
            preferredLanguage = "multi"
        ),
        TuNotWebSource(
            domain = "dominicos.org",
            label = "Orden de Predicadores · España",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 81,
            topics = setOf("tomismo", "teologia", "espiritualidad", "patristica", "biblia")
        ),
        TuNotWebSource(
            domain = "opusdei.org",
            label = "Opus Dei",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 80,
            topics = setOf("espiritualidad", "santidad", "catequesis", "magisterio", "vida cristiana")
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
        ),
        TuNotWebSource(
            domain = "instituteofcatholicculture.org",
            label = "Institute of Catholic Culture",
            authority = TuNotSourceAuthority.CATHOLIC_FORMATION,
            priority = 72,
            topics = setOf("teologia", "filosofia", "biblia", "liturgia", "historia", "espiritualidad"),
            preferredLanguage = "en"
        ),
        TuNotWebSource(
            domain = "formacioncatolica.org",
            label = "Formación Católica",
            authority = TuNotSourceAuthority.CATHOLIC_FORMATION,
            priority = 71,
            topics = setOf("formacion", "espiritualidad", "teologia", "liturgia", "catequesis")
        ),
        TuNotWebSource(
            domain = "catholic.org",
            label = "Catholic Online",
            authority = TuNotSourceAuthority.CATHOLIC_FORMATION,
            priority = 69,
            topics = setOf("santos", "catequesis", "historia", "oracion", "biblia"),
            preferredLanguage = "en"
        ),
        TuNotWebSource(
            domain = "ecatholic2000.com",
            label = "e-Catholic 2000",
            authority = TuNotSourceAuthority.CATHOLIC_FORMATION,
            priority = 68,
            topics = setOf("enciclopedia", "diccionario", "patristica", "oracion", "clasicos"),
            preferredLanguage = "en"
        ),
        TuNotWebSource(
            domain = "catholic-link.com",
            label = "Catholic Link",
            authority = TuNotSourceAuthority.CATHOLIC_MEDIA,
            priority = 63,
            topics = setOf("evangelizacion", "catequesis", "espiritualidad", "pastoral")
        ),
        TuNotWebSource(
            domain = "es.catholic.net",
            label = "Catholic.net",
            authority = TuNotSourceAuthority.CATHOLIC_MEDIA,
            priority = 62,
            topics = setOf("catequesis", "espiritualidad", "familia", "pastoral", "vida cristiana")
        ),
        TuNotWebSource(
            domain = "es.aleteia.org",
            label = "Aleteia Español",
            authority = TuNotSourceAuthority.CATHOLIC_MEDIA,
            priority = 60,
            topics = setOf("actualidad", "espiritualidad", "santos", "vida cristiana")
        ),
        TuNotWebSource(
            domain = "infocatolica.com",
            label = "InfoCatólica",
            authority = TuNotSourceAuthority.CATHOLIC_MEDIA,
            priority = 58,
            topics = setOf("actualidad", "opinion", "iglesia", "formacion")
        ),
        TuNotWebSource(
            domain = "journeysoffaith.com",
            label = "Journeys of Faith",
            authority = TuNotSourceAuthority.CATHOLIC_FORMATION,
            priority = 56,
            topics = setOf("eucaristia", "maria", "santos", "devocion", "espiritualidad"),
            preferredLanguage = "en"
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
        appendLine("1. Santa Sede y documentos oficiales de la Iglesia.")
        appendLine("2. Fuentes católicas de referencia y textos primarios.")
        appendLine("3. Universidades e instituciones académicas católicas.")
        appendLine("4. Recursos católicos de formación.")
        appendLine("5. Medios católicos, solo como apoyo secundario o para actualidad; nunca como fundamento principal de una afirmación doctrinal.")
        appendLine("En una pregunta del tipo 'qué enseña la Iglesia', una fuente académica, formativa o periodística nunca debe contradecir ni sustituir una fuente oficial.")
        appendLine("Distingue doctrina, disciplina vigente, opinión teológica e interpretación académica cuando corresponda.")
        appendLine("No atribuyas al Magisterio una conclusión que solo aparezca en una fuente secundaria.")
        appendLine("No uses como fundamento doctrinal páginas protestantes, evangélicas, ortodoxas, ecuménicas generales, wikis, foros, Reddit, redes sociales, directorios de enlaces o sitios sin autoridad identificable.")
        appendLine("No uses bancos de imágenes o sitios devocionales como fuente doctrinal.")
        appendLine("IDIOMA: responde siempre en español. Si una fuente está en inglés, latín u otro idioma, extrae su contenido relevante y tradúcelo o sintetízalo al español antes de presentarlo. No muestres bloques de texto en inglés salvo petición explícita del usuario. Conserva en idioma original únicamente nombres propios, títulos cuando sea útil y términos técnicos que requieran precisión, acompañándolos de explicación en español.")
        appendLine("Dominios autorizados, de mayor a menor prioridad:")
        sources.forEach { source ->
            appendLine("- ${source.domain} — ${source.label} — ${source.authority.name} — idioma fuente preferente: ${source.preferredLanguage}")
        }
    }
}
