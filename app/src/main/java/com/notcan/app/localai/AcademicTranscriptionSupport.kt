package com.notcan.app.localai

import com.notcan.app.data.local.AcademicVocabularyTermEntity
import com.notcan.app.data.local.DetectedCueEntity
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

/**
 * Contexto académico aplicado después de la inferencia de Whisper.
 *
 * La librería whisper-android usada por NotCan no expone initial_prompt. En vez de
 * sustituir el motor o inventar correcciones, esta capa usa el vocabulario que ya
 * guarda NotCan, conceptos detectados de forma conservadora en apuntes anteriores
 * y léxicos base curados según el tipo de materia.
 *
 * Los glosarios externos se usan únicamente como semilla léxica: NotCan conserva
 * términos, nunca importa automáticamente sus definiciones ni las trata como doctrina.
 */
data class AcademicTranscriptionTerm(
    val value: String,
    val normalized: String,
    val weight: Float,
    val explicit: Boolean
)

object AcademicTranscriptionContext {
    private val whitespace = Regex("\\s+")
    private val capitalizedWord = Regex("\\b\\p{Lu}[\\p{L}\\p{M}'’\\-]{4,}\\b")
    private val emphasizedMarkdown = Regex("(?:\\*\\*|__|\\*|_)([^*_{}`\\n]{5,60})(?:\\*\\*|__|\\*|_)")
    private val ignoredInferred = setOf(
        "importante", "entonces", "tambien", "aunque", "cuando", "donde", "porque",
        "primero", "segundo", "tercero", "finalmente", "pregunta", "respuesta",
        "profesor", "profesora", "documento", "apuntes", "clase", "materia",
        "ejemplo", "explicacion", "conclusion", "introduccion", "recordar"
    )

    /**
     * Léxico dogmático, filosófico y teológico general.
     * Semillas contrastadas con glosarios católicos y teológicos; solo términos.
     */
    private val theologicalBaseTerms = listOf(
        "aseidad", "analogía", "antropomorfismo", "atributos de Dios",
        "atributos morales", "atributos metafísicos", "visión beatífica",
        "ser contingente", "ser necesario", "causa eficiente", "causa final",
        "causa formal", "argumento cosmológico", "esencia", "existencia",
        "eternidad", "expiación", "providencia", "revelación", "inspiración",
        "Tradición", "Magisterio", "Encarnación", "unión hipostática",
        "Cristología", "Soteriología", "Eclesiología", "Escatología",
        "Pneumatología", "Mariología", "antropología teológica",
        "teología dogmática", "teología moral", "teología fundamental",
        "gracia santificante", "gracia actual", "gracia habitual",
        "pecado original", "justificación", "santificación", "redención",
        "economía de la salvación", "misterio pascual", "filiación divina",
        "creación ex nihilo", "Santísima Trinidad", "naturaleza humana",
        "naturaleza divina", "transubstanciación", "consustancial",
        "consubstancialidad", "homoousios", "ousía", "hipóstasis", "physis",
        "prosopon", "perichóresis", "communicatio idiomatum", "Imago Dei",
        "kénosis", "Theotokos", "sacramentalidad", "ex opere operato",
        "epíclesis", "anámnesis", "parusía", "sensus fidei", "depositum fidei",
        "lex orandi", "lex credendi", "Eucaristía", "Bautismo", "Confirmación",
        "Orden sacerdotal", "Unción de los enfermos", "Concilio ecuménico",
        "Padres apostólicos", "Patrística", "ontología", "metafísica",
        "hilemorfismo", "potencia y acto", "ley natural", "dignidad humana",
        "bioética", "adopcionismo", "apolinarismo", "arrianismo", "docetismo",
        "gnosticismo", "monofisismo", "monotelismo", "nestorianismo",
        "pelagianismo", "semipelagianismo", "modalismo", "monarquianismo",
        "patripasianismo", "macedonianismo", "iconoclasia"
    )

    /**
     * Léxico bíblico. Las semillas proceden de vocabularios bíblicos católicos,
     * Clerus/Biblia Clerus, índices de vocabulario bíblico y glosarios generales.
     */
    private val biblicalBaseTerms = listOf(
        "Abrahán", "Adonai", "Alianza", "arca de la Alianza", "anatema",
        "anástasis", "apocalíptica", "apócrifos", "canon bíblico",
        "protocanónicos", "deuterocanónicos", "Pentateuco", "Torá", "Decálogo",
        "Éxodo", "Pascua", "profeta", "profecía", "mesianismo", "Mesías",
        "Reino de Dios", "Hijo del Hombre", "Siervo de Yahvé", "Yahvé",
        "Israel", "Judá", "Sión", "Jerusalén", "Canaán", "Efraín", "Levitas",
        "saduceos", "fariseos", "esenios", "escribas", "sanedrín", "sinagoga",
        "Templo de Jerusalén", "Sheol", "Gehenna", "jubileo", "goel", "hesed",
        "ruah", "shekinah", "berit", "qahal", "ekklesía", "agápe", "koinonía",
        "diakonía", "martyría", "parousía", "Logos", "Verbo", "kerigma",
        "parábola", "bienaventuranzas", "Septuaginta", "Vulgata", "texto masorético",
        "Qumrán", "Midrash", "Talmud", "Targum", "evangelios sinópticos",
        "tradición sinóptica", "fuente Q", "exégesis", "hermenéutica",
        "crítica textual", "crítica histórica", "crítica de las formas",
        "crítica de la redacción", "tipología", "sensus plenior", "lectio divina"
    )

    /**
     * Léxico eclesiástico/canónico y de organización de la Iglesia. Se alimenta
     * de glosarios diocesanos oficiales y terminología jurídica católica.
     */
    private val ecclesialBaseTerms = listOf(
        "abad", "abadesa", "nuncio apostólico", "delegado apostólico", "arzobispo",
        "archidiócesis", "obispo auxiliar", "obispo coadjutor", "obispo diocesano",
        "conferencia episcopal", "Colegio de Cardenales", "cónclave", "curia",
        "Curia Romana", "curia diocesana", "diácono", "diaconado", "dicasterio",
        "eparquía", "excomunión", "facultad", "jerarquía", "Santa Sede",
        "vicario episcopal", "vicario general", "tribunal eclesiástico",
        "decreto de nulidad", "matrimonio putativo", "laicización", "prelatura",
        "presbítero", "presbiterio", "parroquia", "párroco", "canónigo",
        "Derecho canónico", "Código de Derecho Canónico", "ordinario del lugar",
        "potestad de régimen", "fuero interno", "fuero externo", "incardinación",
        "letras dimisorias", "dispensa", "impedimento", "irregularidad", "censura",
        "interdicto", "Colegio episcopal", "sínodo", "sínodo diocesano",
        "concilio particular", "metropolitano", "provincia eclesiástica",
        "nunciatura apostólica", "sede vacante", "sede impedida",
        "administrador diocesano", "cabildo catedralicio", "Rota Romana",
        "Penitenciaría Apostólica", "Signatura Apostólica"
    )

    private val theologicalContextMarkers = listOf(
        "teolog", "cristolog", "patrolog", "patrist", "eclesiolog", "soteriolog",
        "escatolog", "pneumatolog", "mariolog", "sacrament", "dogmat", "fundamental",
        "filosof", "metafis", "ontolog", "antropolog", "bioet", "moral",
        "revelacion", "revelación", "padres de la iglesia"
    )

    private val biblicalContextMarkers = listOf(
        "biblia", "biblic", "escritura", "antiguo testamento", "nuevo testamento",
        "evangelio", "sinoptic", "exeg", "hermeneut", "hebreo", "griego",
        "salmo", "profeta", "pentateuco", "torá", "tora", "paulino", "juanino",
        "apocalipsis", "septuaginta", "vulgata", "qumran", "qumrán"
    )

    private val ecclesialContextMarkers = listOf(
        "canon", "derecho", "eclesiast", "liturg", "sacrament", "pastoral",
        "diocesis", "diócesis", "arquidiocesis", "arquidiócesis", "parroquia",
        "curia", "ministerio", "orden sacerdotal", "obispo", "presbiter",
        "tribunal", "matrimonio", "nulidad", "sede vacante"
    )

    fun buildTerms(
        subjectName: String?,
        classTitle: String?,
        stored: List<AcademicVocabularyTermEntity>,
        contextTexts: List<String> = emptyList()
    ): List<AcademicTranscriptionTerm> {
        val terms = linkedMapOf<String, AcademicTranscriptionTerm>()

        fun add(raw: String, weight: Float, explicit: Boolean) {
            val value = raw.trim().replace(whitespace, " ").trim(' ', ',', '.', ';', ':', '-', '—')
            val normalized = normalize(value)
            if (value.length < 4 || normalized.length < 4) return
            if (!explicit && value.length < 9 && !value.contains(' ')) return
            if (!explicit && normalized in ignoredInferred) return
            val candidate = AcademicTranscriptionTerm(value, normalized, weight, explicit)
            val current = terms[normalized]
            if (current == null || candidate.weight > current.weight || (candidate.explicit && !current.explicit)) {
                terms[normalized] = candidate
            }
        }

        stored.asSequence()
            .filter { it.language.equals("es", ignoreCase = true) || it.language.isBlank() }
            .sortedByDescending { it.weight }
            .take(MAX_STORED_TERMS)
            .forEach { term ->
                val userConfirmed = !term.source.startsWith("auto", ignoreCase = true)
                add(term.term, term.weight.coerceAtLeast(1f), explicit = userConfirmed)
            }

        if (matchesContext(theologicalContextMarkers, subjectName, classTitle, contextTexts)) {
            theologicalBaseTerms.forEach { add(it, 1.35f, explicit = true) }
        }
        if (matchesContext(biblicalContextMarkers, subjectName, classTitle, contextTexts)) {
            biblicalBaseTerms.forEach { add(it, 1.32f, explicit = true) }
        }
        if (matchesContext(ecclesialContextMarkers, subjectName, classTitle, contextTexts)) {
            ecclesialBaseTerms.forEach { add(it, 1.30f, explicit = true) }
        }

        inferFromContext(contextTexts).forEach { add(it, 1.6f, explicit = false) }
        subjectName?.let { add(it, 3.5f, explicit = false) }
        classTitle
            ?.takeUnless { title -> subjectName != null && title.startsWith(subjectName, ignoreCase = true) && '#' in title }
            ?.let { add(it, 2.5f, explicit = false) }

        return terms.values
            .sortedWith(compareByDescending<AcademicTranscriptionTerm> { it.explicit }.thenByDescending { it.weight })
            .take(MAX_CONTEXT_TERMS)
    }

    private fun matchesContext(
        markers: List<String>,
        subjectName: String?,
        classTitle: String?,
        contextTexts: List<String>
    ): Boolean {
        val sample = buildString {
            append(subjectName.orEmpty())
            append(' ')
            append(classTitle.orEmpty())
            contextTexts.asSequence().take(3).forEach { text ->
                append(' ')
                append(text.take(1_000))
            }
        }
        val normalized = normalize(sample)
        return markers.any { marker -> normalized.contains(normalize(marker)) }
    }

    private fun inferFromContext(texts: List<String>): List<String> {
        if (texts.isEmpty()) return emptyList()
        val found = linkedSetOf<String>()
        texts.asSequence().take(MAX_CONTEXT_TEXTS).forEach { source ->
            val text = source
                .replace(Regex("<[^>]+>"), " ")
                .replace(Regex("[>#]+"), " ")
                .take(MAX_CONTEXT_CHARS_PER_TEXT)

            text.lineSequence()
                .map { it.trim().replace(whitespace, " ") }
                .filter { line ->
                    line.length in 9..64 &&
                        line.split(whitespace).size in 2..7 &&
                        !line.endsWith('.') && !line.endsWith('?') && !line.endsWith('!')
                }
                .take(12)
                .forEach(found::add)

            capitalizedWord.findAll(text).map { it.value }.forEach { candidate ->
                if (normalize(candidate) !in ignoredInferred) found += candidate
            }

            emphasizedMarkdown.findAll(text).map { it.groupValues[1].trim() }
                .filter { it.length in 5..60 }
                .forEach(found::add)
        }
        return found.take(MAX_INFERRED_TERMS)
    }

    fun correct(
        result: WhisperTranscriptionResult,
        terms: List<AcademicTranscriptionTerm>
    ): WhisperTranscriptionResult {
        if (terms.isEmpty()) return result
        if (result.segments.isEmpty()) {
            val corrected = correctText(result.text, terms)
            return result.copy(text = corrected)
        }
        val correctedSegments = result.segments.map { segment ->
            segment.copy(text = correctText(segment.text, terms))
        }
        return result.copy(
            text = correctedSegments.joinToString(" ") { it.text }.replace(whitespace, " ").trim(),
            segments = correctedSegments
        )
    }

    private fun correctText(text: String, terms: List<AcademicTranscriptionTerm>): String {
        if (text.isBlank()) return text
        val tokens = text.split(whitespace).map(::Token).toMutableList()
        val candidates = terms
            .filter { it.value.length >= 5 }
            .sortedWith(
                compareByDescending<AcademicTranscriptionTerm> { it.value.count { char -> char.isWhitespace() } }
                    .thenByDescending { it.explicit }
                    .thenByDescending { it.weight }
                    .thenByDescending { it.value.length }
            )

        for (candidate in candidates) {
            val replacementWords = candidate.value.split(whitespace)
            val count = replacementWords.size
            if (count !in 1..4 || tokens.size < count) continue

            var index = 0
            while (index <= tokens.size - count) {
                val window = tokens.subList(index, index + count)
                if (window.any { it.core.isBlank() }) {
                    index++
                    continue
                }
                val currentWords = window.map { normalize(it.core) }
                val current = currentWords.joinToString(" ")
                if (current == candidate.normalized) {
                    index += count
                    continue
                }
                val targetWords = replacementWords.map(::normalize)
                val firstAligned = currentWords.firstOrNull()?.firstOrNull() == targetWords.firstOrNull()?.firstOrNull()
                if (!firstAligned) {
                    index++
                    continue
                }
                val alignedInitials = currentWords.zip(targetWords).count { (a, b) -> a.firstOrNull() == b.firstOrNull() }
                if (alignedInitials * 2 < count) {
                    index++
                    continue
                }
                val distance = levenshtein(current, candidate.normalized)
                val allowed = allowedDistance(candidate.normalized.length, count, candidate.explicit)
                if (distance == 0 || distance > allowed) {
                    index++
                    continue
                }

                val originalStartsUpper = window.first().core.firstOrNull()?.isUpperCase() == true
                replacementWords.forEachIndexed { offset, replacement ->
                    val resolved = if (offset == 0 && originalStartsUpper) {
                        replacement.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }
                    } else replacement
                    window[offset].core = resolved
                }
                index += count
            }
        }
        return tokens.joinToString(" ") { it.render() }
    }

    private fun allowedDistance(length: Int, wordCount: Int, explicit: Boolean): Int {
        val base = when {
            wordCount > 1 -> max(2, (length * 0.14f).toInt())
            length <= 8 -> 1
            length <= 13 -> 2
            else -> 3
        }
        return if (explicit) base else minOf(base, 2)
    }

    private class Token(raw: String) {
        private val prefix: String
        var core: String
        private val suffix: String

        init {
            var start = 0
            while (start < raw.length && !raw[start].isLetterOrDigit()) start++
            var end = raw.length
            while (end > start && !raw[end - 1].isLetterOrDigit()) end--
            prefix = raw.substring(0, start)
            core = raw.substring(start, end)
            suffix = raw.substring(end)
        }

        fun render(): String = prefix + core + suffix
    }

    internal fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(whitespace, " ")

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private const val MAX_STORED_TERMS = 220
    private const val MAX_CONTEXT_TERMS = 320
    private const val MAX_INFERRED_TERMS = 80
    private const val MAX_CONTEXT_TEXTS = 12
    private const val MAX_CONTEXT_CHARS_PER_TEXT = 12_000
}

/**
 * Capítulos temporales generados localmente a partir de pausas, transiciones y
 * duración. El título prioriza vocabulario académico realmente presente en el
 * bloque; no modifica la transcripción ni requiere Internet.
 */
object ClassChapterDetector {
    private val transition = Regex(
        "^(ahora|bien|entonces|pasemos|pasamos|vamos a|veamos|otro punto|otro tema|siguiente|por otra parte|en segundo lugar|en tercer lugar|finalmente|para terminar|para concluir)\\b",
        RegexOption.IGNORE_CASE
    )

    fun detect(
        segments: List<WhisperSegmentResult>,
        terms: List<AcademicTranscriptionTerm>,
        classSessionId: String,
        transcriptId: String,
        audioId: String?
    ): List<DetectedCueEntity> {
        if (segments.isEmpty()) return emptyList()
        val boundaries = mutableListOf(0)
        var lastBoundaryMs = segments.first().startMs

        for (index in 1 until segments.size) {
            val current = segments[index]
            val previous = segments[index - 1]
            val elapsed = current.startMs - lastBoundaryMs
            val gap = (current.startMs - previous.endMs).coerceAtLeast(0L)
            val transitionDetected = transition.containsMatchIn(current.text.trim())
            val naturalBoundary = elapsed >= MIN_CHAPTER_MS && (gap >= PAUSE_BOUNDARY_MS || transitionDetected)
            val forcedBoundary = elapsed >= MAX_CHAPTER_MS
            if (naturalBoundary || forcedBoundary) {
                boundaries += index
                lastBoundaryMs = current.startMs
                if (boundaries.size >= MAX_CHAPTERS) break
            }
        }

        val now = System.currentTimeMillis()
        return boundaries.mapIndexed { chapterIndex, startIndex ->
            val endIndexExclusive = boundaries.getOrNull(chapterIndex + 1) ?: segments.size
            val chapterSegments = segments.subList(startIndex, endIndexExclusive)
            val blockText = chapterSegments.joinToString(" ") { it.text }
                .replace(Regex("\\s+"), " ")
                .trim()
            val title = chooseTitle(blockText, terms, chapterIndex)
            val startMs = chapterSegments.first().startMs
            DetectedCueEntity(
                id = "chapter-$transcriptId-$chapterIndex",
                classSessionId = classSessionId,
                transcriptId = transcriptId,
                audioId = audioId,
                label = "🧭 Capítulo · ${formatTimestamp(startMs)}",
                keyword = title,
                excerpt = buildString {
                    append(title)
                    blockText.takeIf { it.isNotBlank() }?.let {
                        append("\n")
                        append(it.take(320))
                    }
                },
                offsetMs = startMs,
                createdAtEpochMs = now + chapterIndex
            )
        }
    }

    private fun chooseTitle(
        blockText: String,
        terms: List<AcademicTranscriptionTerm>,
        chapterIndex: Int
    ): String {
        val normalizedBlock = AcademicTranscriptionContext.normalize(blockText.take(5_000))
        val matchedTerms = terms.asSequence()
            .filter { it.normalized.length >= 5 && normalizedBlock.contains(it.normalized) }
            .sortedWith(compareByDescending<AcademicTranscriptionTerm> { it.weight }.thenByDescending { it.value.length })
            .map { it.value }
            .distinctBy { AcademicTranscriptionContext.normalize(it) }
            .take(2)
            .toList()
        if (matchedTerms.isNotEmpty()) return matchedTerms.joinToString(" · ")

        val candidate = blockText
            .split(Regex("(?<=[.!?])\\s+"))
            .firstOrNull { it.length >= 18 }
            .orEmpty()
            .replace(transition, "")
            .trim(' ', ',', '.', ';', ':', '-', '—')
        val words = candidate.split(Regex("\\s+")).filter { it.isNotBlank() }.take(7)
        return words.joinToString(" ")
            .takeIf { it.length >= 8 }
            ?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
            ?: if (chapterIndex == 0) "Inicio de la clase" else "Continuación de la clase"
    }

    private fun formatTimestamp(ms: Long): String {
        val seconds = (ms / 1_000L).coerceAtLeast(0L)
        val hours = seconds / 3_600L
        val minutes = (seconds % 3_600L) / 60L
        val remainder = seconds % 60L
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remainder)
        else "%02d:%02d".format(minutes, remainder)
    }

    private const val PAUSE_BOUNDARY_MS = 3_000L
    private const val MIN_CHAPTER_MS = 3 * 60_000L
    private const val MAX_CHAPTER_MS = 12 * 60_000L
    private const val MAX_CHAPTERS = 18
}
