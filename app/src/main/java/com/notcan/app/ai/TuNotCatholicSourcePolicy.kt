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
    val preferredLanguage: String = "es",
    val searchable: Boolean = true,
    val note: String? = null
)

/**
 * Lista blanca de fuentes web para TuNot en consultas católicas/doctrinales.
 *
 * La jerarquía distingue entre fuentes universales de la Iglesia, fuentes oficiales
 * locales, referencias católicas, instituciones académicas, formación y medios.
 * Un sitio oficial diocesano puede ser excelente para terminología o disciplina local,
 * pero nunca sustituye al Magisterio universal cuando la pregunta es doctrinal.
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
            domain = "archspm.org",
            label = "Arquidiócesis de Saint Paul y Minneapolis",
            authority = TuNotSourceAuthority.OFFICIAL_CHURCH,
            priority = 87,
            topics = setOf("glosario", "terminologia eclesiastica", "derecho canonico", "pastoral"),
            note = "Fuente oficial diocesana; útil para terminología y práctica eclesial. No sustituye al Magisterio universal."
        ),
        TuNotWebSource(
            domain = "diocesisdesantander.com",
            label = "Diócesis de Santander",
            authority = TuNotSourceAuthority.OFFICIAL_CHURCH,
            priority = 87,
            topics = setOf("glosario", "terminologia eclesiastica", "derecho canonico", "liturgia", "pastoral"),
            note = "Fuente oficial diocesana; priorizar CIC, CCE y Santa Sede para doctrina o derecho universal."
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
            domain = "clerus.org",
            label = "Clerus · Biblia Clerus",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 83,
            topics = setOf("biblia", "vocabulario biblico", "exegesis", "escritura", "teologia biblica"),
            note = "Referencia bíblica y léxica. Verificar doctrina o disciplina vigente con fuentes oficiales actuales."
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
            domain = "ec.aciprensa.com",
            label = "Enciclopedia Católica · ACI Prensa",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 79,
            topics = setOf("enciclopedia catolica", "teologia", "historia", "santos", "liturgia", "doctrina", "glosario"),
            note = "Fuente enciclopédica secundaria. Para 'qué enseña la Iglesia', priorizar documentos oficiales enlazados o equivalentes."
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
        ),
        TuNotWebSource(
            domain = "magisterium.com",
            label = "Magisterium AI",
            authority = TuNotSourceAuthority.CATHOLIC_REFERENCE,
            priority = 40,
            topics = setOf("magisterio", "catecismo", "teologia", "patristica", "doctrina"),
            searchable = false,
            note = "Solo referencia conceptual. No invocar su IA, API ni servicios con cobro por tokens desde TuNot."
        )
    ).sortedByDescending { it.priority }

    private val searchableDomains = sources
        .filter { it.searchable }
        .map { it.domain.lowercase() }
        .toSet()

    fun isAllowedUrl(url: String): Boolean {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) return false
        return searchableDomains.any { domain -> host == domain || host.endsWith(".$domain") }
    }

    fun sourceForUrl(url: String): TuNotWebSource? {
        val host = runCatching { URI(url).host.orEmpty().lowercase() }.getOrDefault("")
        if (host.isBlank()) return null
        return sources.firstOrNull { source -> host == source.domain || host.endsWith(".${source.domain}") }
    }

    fun promptPolicy(): String = buildString {
        appendLine("POLÍTICA CATÓLICA DE FUENTES WEB DE TUNOT")
        appendLine("TuNot es un tutor académico católico. Para consultas doctrinales no debe presentar como doctrina católica una opinión cristiana general, ecuménica o de otra confesión.")
        appendLine("En consultas católicas o doctrinales utiliza dominios autorizados y respeta este orden de autoridad:")
        appendLine("1. Santa Sede y documentos oficiales universales de la Iglesia.")
        appendLine("2. Sitios oficiales de diócesis/arquidiócesis para información de su competencia; no sustituyen al Magisterio universal.")
        appendLine("3. Fuentes católicas de referencia y textos primarios.")
        appendLine("4. Universidades e instituciones académicas católicas.")
        appendLine("5. Recursos católicos de formación.")
        appendLine("6. Medios católicos, solo como apoyo secundario o para actualidad.")
        appendLine("En una pregunta del tipo 'qué enseña la Iglesia', una fuente diocesana, académica, formativa, enciclopédica o periodística nunca debe contradecir ni sustituir una fuente oficial universal.")
        appendLine("Distingue doctrina, disciplina vigente, opinión teológica e interpretación académica cuando corresponda.")
        appendLine("No atribuyas al Magisterio una conclusión que solo aparezca en una fuente secundaria.")
        appendLine("No uses como fundamento doctrinal páginas protestantes, evangélicas, ortodoxas, ecuménicas generales, wikis NO autorizadas, foros, Reddit, redes sociales, directorios de enlaces o sitios sin autoridad identificable.")
        appendLine("El formato wiki de un dominio incluido expresamente en esta lista blanca (por ejemplo ec.aciprensa.com) no lo desautoriza por sí mismo.")
        appendLine("No uses bancos de imágenes o sitios devocionales como fuente doctrinal.")
        appendLine("Magisterium AI queda SOLO COMO REFERENCIA CONCEPTUAL: TuNot no debe invocar su IA, API ni ningún servicio de Magisterium que genere cargos por tokens. Si una idea conocida por esa plataforma resulta útil, debe preferirse la fuente primaria católica equivalente disponible en la lista blanca.")
        appendLine("IDIOMA: responde siempre en español. Si una fuente está en inglés, francés, italiano, alemán u otro idioma moderno, extrae su contenido relevante y tradúcelo o sintetízalo al español antes de presentarlo.")
        appendLine("LATÍN: no muestres términos, frases ni bloques en latín por defecto. Tradúcelos al español y conserva la referencia. Muestra latín únicamente cuando el usuario lo pida o cuando la tarea sea específicamente lingüística/bilingüe.")
        appendLine("Dominios católicos configurados, de mayor a menor prioridad:")
        sources.forEach { source ->
            val access = if (source.searchable) "buscable" else "solo referencia"
            val note = source.note?.let { " — $it" }.orEmpty()
            appendLine("- ${source.domain} — ${source.label} — ${source.authority.name} — $access — idioma fuente preferente: ${source.preferredLanguage}$note")
        }
    }
}
