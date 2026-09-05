package com.notcan.app.ai

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.notcan.app.localai.GemmaLiteRtModelManager
import com.notcan.app.localai.GemmaLiteRtModelState
import com.notcan.app.localai.GemmaRuntimeCache
import com.notcan.app.settings.NotCanPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.text.Normalizer

/**
 * Experimental on-device TuNot engine using Google's LiteRT-LM and Gemma 4 E2B.
 *
 * The model always runs locally. Internet is only needed to download it; web access,
 * when added later, will be provided explicitly by NotCan rather than by the model.
 */
class LiteRtGemmaTuNotEngine(context: Context) {
    private val appContext = context.applicationContext
    private val modelManager = GemmaLiteRtModelManager(appContext)
    private val preferences = NotCanPreferences(appContext)
    private val runtimePrefs = appContext.getSharedPreferences("notcan_gemma_runtime", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val performanceMetrics = com.notcan.app.performance.PerformanceMetricsStore(appContext)
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var idleReleaseJob: Job? = null

    private data class EngineHolder(val engine: Engine, val backendLabel: String)
    private data class SourceChunk(val label: String, val text: String, val score: Int = 0)

    private class GenerationException(
        val backendLabel: String,
        val elapsedMs: Long,
        val generatedChars: Int,
        val partialText: String,
        cause: Throwable
    ) : IllegalStateException(
        "$backendLabel: ${cause.message ?: cause.javaClass.simpleName}; $elapsedMs ms; $generatedChars caracteres generados",
        cause
    )

    data class Answer(val text: String, val backendLabel: String)

    @Volatile
    private var holder: EngineHolder? = null

    @Volatile
    private var lastAnswerText: String = ""

    @Volatile
    private var lastAnswerSubject: String = ""

    fun isAvailable(): Boolean = runCatching {
        modelManager.state() == GemmaLiteRtModelState.INSTALLED
    }.getOrDefault(false)

    suspend fun warmUp(): String? = mutex.withLock {
        if (!isAvailable()) return@withLock null
        idleReleaseJob?.cancel()
        val engineWasWarm = holder != null
        val startedAt = SystemClock.elapsedRealtime()
        val ready = ensureEngineReady()
        if (!engineWasWarm) {
            performanceMetrics.recordGemmaLoad(
                (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),
                ready.backendLabel
            )
        }
        scheduleIdleRelease()
        ready.backendLabel
    }

    suspend fun answer(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String,
        strictSources: Boolean,
        intentQuestion: String = question,
        webContext: String = "",
        vocabularyContext: String = "",
        pedagogicalMode: Boolean = false,
        onPartial: ((text: String, backendLabel: String) -> Unit)? = null
    ): Answer = mutex.withLock {
        check(isAvailable()) { "Gemma 4 LiteRT-LM no está instalado" }
        idleReleaseJob?.cancel()

        val subjectKey = subjectName.orEmpty()
        val followUpContext = lastAnswerText
            .takeIf {
                it.isNotBlank() &&
                    lastAnswerSubject == subjectKey &&
                    isResponseTransformRequest(intentQuestion)
            }
            .orEmpty()

        val sourceContext = if (followUpContext.isNotBlank()) {
            ""
        } else {
            buildSourceContext(subjectName, notes, transcript, intentQuestion, strictSources)
        }

        val prompt = buildString {
            if (strictSources) {
                appendLine("Responde únicamente con el material de NotCan incluido abajo.")
                appendLine("Si la respuesta no consta en ese material, responde exactamente: No consta en las fuentes disponibles.")
            } else {
                appendLine("Usa el material de NotCan cuando sea pertinente. Puedes complementar con conocimiento general fiable.")
            }
            if (followUpContext.isNotBlank()) {
                appendLine()
                appendLine("--- RESPUESTA ANTERIOR DE TUNOT QUE EL ESTUDIANTE QUIERE TRANSFORMAR ---")
                appendLine(followUpContext.take(MAX_FOLLOW_UP_CHARS))
                appendLine("--- FIN DE LA RESPUESTA ANTERIOR ---")
                appendLine("La solicitud actual se refiere a esa respuesta anterior. No vuelvas a analizar toda la clase salvo que el estudiante lo pida.")
            } else if (sourceContext.isNotBlank()) {
                appendLine()
                appendLine("--- MATERIAL DE NOTCAN RELEVANTE PARA ESTA PREGUNTA ---")
                appendLine(sourceContext)
                appendLine("--- FIN DEL MATERIAL ---")
            }
            if (vocabularyContext.isNotBlank() && (!isSimpleDefinition(intentQuestion) || strictSources)) {
                appendLine()
                appendLine("--- VOCABULARIO ACADÉMICO DE NOTCAN ---")
                appendLine(vocabularyContext.take(MAX_VOCAB_CONTEXT_CHARS))
                appendLine("Estos términos sirven para reconocer grafías y terminología; por sí solos no son definiciones ni prueba doctrinal.")
                appendLine("--- FIN DEL VOCABULARIO ---")
            }
            if (!strictSources && webContext.isNotBlank()) {
                appendLine()
                appendLine("--- FUENTES WEB RECUPERADAS POR NOTCAN ---")
                appendLine(webContext.take(MAX_WEB_CONTEXT_CHARS))
                appendLine("Usa solo URLs y datos presentes aquí. Si citas web, menciona título/URL sin inventarlos.")
                appendLine("--- FIN DE FUENTES WEB ---")
            }
            appendLine()
            appendLine(responseLengthInstruction(intentQuestion))
            appendLine("Pregunta actual del estudiante:")
            append(question.trim())
        }

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(buildAdaptiveSystemInstruction(strictSources, pedagogicalMode, intentQuestion)),
            samplerConfig = SamplerConfig(
                topK = TOP_K,
                topP = TOP_P,
                temperature = if (isStudyArtifactRequest(intentQuestion)) STRUCTURED_TEMPERATURE else TEMPERATURE
            )
        )

        val generationTimeoutMs = generationTimeoutMs(intentQuestion)
        val maxOutputChars = outputCharBudget(intentQuestion)
        val engineWasWarm = holder != null
        val engineLoadStartedAt = SystemClock.elapsedRealtime()
        val primaryHolder = ensureEngineReady()
        if (!engineWasWarm) {
            performanceMetrics.recordGemmaLoad(
                (SystemClock.elapsedRealtime() - engineLoadStartedAt).coerceAtLeast(0L),
                primaryHolder.backendLabel
            )
        }
        val answer = try {
            generate(primaryHolder, prompt, conversationConfig, generationTimeoutMs, maxOutputChars, onPartial)
        } catch (t: GenerationException) {
            val recovered = recoverUsefulPartial(t.partialText)
            if (recovered != null) {
                resetEngine()
                Answer(recovered, "${t.backendLabel} · respuesta recuperada")
            } else if (primaryHolder.backendLabel == "GPU" && t.generatedChars == 0) {
                performanceMetrics.recordGemmaFallback("GPU sin primer token en ${GPU_FIRST_TOKEN_TIMEOUT_MS / 1_000L} s")
                resetEngine()
                val cpuHolder = ensureCpuEngineReady("CPU respaldo")
                generate(cpuHolder, prompt, conversationConfig, generationTimeoutMs, maxOutputChars, onPartial)
            } else {
                resetEngine()
                throw t
            }
        } catch (t: Throwable) {
            resetEngine()
            throw t
        }

        lastAnswerText = answer.text
        lastAnswerSubject = subjectKey
        scheduleIdleRelease()
        answer
    }

    private suspend fun generate(
        engineHolder: EngineHolder,
        prompt: String,
        conversationConfig: ConversationConfig,
        timeoutMs: Long,
        maxOutputChars: Int,
        onPartial: ((text: String, backendLabel: String) -> Unit)?
    ): Answer = coroutineScope {
        val output = StringBuilder()
        val generationStartedAt = SystemClock.elapsedRealtime()
        var firstTokenMs = 0L
        try {
            withTimeout(timeoutMs) {
                engineHolder.engine.createConversation(conversationConfig).use { conversation ->
                    val messages = conversation.sendMessageAsync(prompt).produceIn(this)
                    val firstMessage = if (engineHolder.backendLabel == "GPU") {
                        withTimeout(GPU_FIRST_TOKEN_TIMEOUT_MS) {
                            messages.receiveCatching().getOrNull()
                        }
                    } else {
                        messages.receiveCatching().getOrNull()
                    }

                    var stopRequested = false
                    fun appendDelta(delta: String) {
                        if (delta.isEmpty() || stopRequested) return
                        if (firstTokenMs == 0L) firstTokenMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)
                        output.append(delta)
                        onPartial?.invoke(cleanModelText(output.toString()), engineHolder.backendLabel)
                        if (shouldStopGeneration(output, maxOutputChars)) stopRequested = true
                    }

                    firstMessage?.toString()?.let(::appendDelta)
                    if (stopRequested) {
                        messages.cancel()
                    } else {
                        for (message in messages) {
                            appendDelta(message.toString())
                            if (stopRequested) {
                                messages.cancel()
                                break
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            throw GenerationException(
                backendLabel = engineHolder.backendLabel,
                elapsedMs = SystemClock.elapsedRealtime() - generationStartedAt,
                generatedChars = output.length,
                partialText = output.toString(),
                cause = t
            )
        }

        val text = finalizeBoundedOutput(output.toString(), maxOutputChars)
        if (text.isBlank()) {
            throw GenerationException(
                backendLabel = engineHolder.backendLabel,
                elapsedMs = SystemClock.elapsedRealtime() - generationStartedAt,
                generatedChars = 0,
                partialText = "",
                cause = IllegalStateException("Gemma 4 no produjo texto utilizable")
            )
        }
        val totalMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)
        performanceMetrics.recordGemmaGeneration(
            backend = engineHolder.backendLabel,
            firstTokenMs = firstTokenMs.takeIf { it > 0L } ?: totalMs,
            totalMs = totalMs,
            outputChars = text.length,
            promptChars = prompt.length
        )
        Answer(text = text, backendLabel = engineHolder.backendLabel)
    }

    private fun cleanModelText(raw: String): String {
        if (raw.isBlank() || looksLikeStudyArtifact(raw)) return raw.trim()
        return raw
            .replace(Regex("""\$\s*\\text\{([^{}]+)\}\s*\$""")) { it.groupValues[1] }
            .replace(Regex("""\\text\{([^{}]+)\}""")) { it.groupValues[1] }
            .replace(Regex("""\\\(([^)\n]+)\\\)""")) { it.groupValues[1].trim() }
            .replace(Regex("""\\\[([^\]\n]+)\\\]""")) { it.groupValues[1].trim() }
            .replace(Regex("""\$\s*([^$\n]{1,120})\s*\$""")) { it.groupValues[1].trim() }
            .trim()
    }

    /**
     * Builds a small local RAG context instead of blindly taking the tail of every source.
     * This keeps document titles and paragraphs related to the current question even when
     * the original DOCX/transcript is much larger than the model context window.
     */
    private fun buildSourceContext(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String,
        strictSources: Boolean
    ): String {
        val chunks = buildList {
            addAll(chunkSource("Apuntes", notes))
            addAll(chunkSource("Transcripción / archivos", transcript))
        }
        if (chunks.isEmpty()) return ""

        val tokens = queryTokens(question)
        val artifactRequest = isStudyArtifactRequest(question)
        val simpleDefinition = isSimpleDefinition(question)
        val broadRequest = isBroadSourceRequest(question)
        val sourceOverviewRequest = isSourceOverviewRequest(question)
        val scored = chunks.map { chunk ->
            chunk.copy(score = scoreChunk(chunk.text, tokens))
        }

        val selected = when {
            artifactRequest -> evenlySample(scored, ARTIFACT_SELECTED_CHUNKS)
            broadRequest -> evenlySample(scored, BROAD_SELECTED_CHUNKS)
            sourceOverviewRequest && tokens.isNotEmpty() -> scored
                .filter { it.score > 0 }
                .sortedByDescending { it.score }
                .take(OVERVIEW_SELECTED_CHUNKS)
            tokens.isNotEmpty() -> scored
                .filter { it.score > 0 }
                .sortedByDescending { it.score }
                .take(if (simpleDefinition && !strictSources) SIMPLE_DEFINITION_SELECTED_CHUNKS else FOCUSED_SELECTED_CHUNKS)
            else -> emptyList()
        }.ifEmpty {
            // A strict-source question may be a paraphrase with no exact lexical hit.
            // Keep a small representative fallback instead of paying the cost of the whole class.
            if (strictSources) evenlySample(scored, FOCUSED_SELECTED_CHUNKS) else emptyList()
        }

        if (selected.isEmpty()) return ""
        val sourceCharLimit = when {
            artifactRequest -> MAX_ARTIFACT_SOURCE_CHARS
            simpleDefinition && !strictSources -> MAX_SIMPLE_DEFINITION_SOURCE_CHARS
            broadRequest -> MAX_BROAD_SOURCE_CHARS
            sourceOverviewRequest -> MAX_OVERVIEW_SOURCE_CHARS
            else -> MAX_FOCUSED_SOURCE_CHARS
        }
        return buildString {
            subjectName?.takeIf { it.isNotBlank() }?.let { appendLine("Materia: $it") }
            selected.forEachIndexed { index, chunk ->
                if (index > 0) appendLine()
                appendLine("[${chunk.label}]")
                appendLine(chunk.text)
            }
        }.take(sourceCharLimit)
    }

    private fun chunkSource(label: String, raw: String): List<SourceChunk> {
        val cleaned = raw
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace(Regex("[ \\t\\r]+"), " ")
            .replace(Regex("\\n{3,}"), "\\n\\n")
            .trim()
        if (cleaned.isBlank()) return emptyList()

        val paragraphs = cleaned
            .split(Regex("\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val result = mutableListOf<SourceChunk>()
        val current = StringBuilder()
        fun flush() {
            val text = current.toString().trim()
            if (text.isNotBlank()) result += SourceChunk(label, text)
            current.clear()
        }

        for (paragraph in paragraphs) {
            if (paragraph.length > SOURCE_CHUNK_CHARS) {
                flush()
                var start = 0
                while (start < paragraph.length) {
                    val end = (start + SOURCE_CHUNK_CHARS).coerceAtMost(paragraph.length)
                    result += SourceChunk(label, paragraph.substring(start, end).trim())
                    if (end >= paragraph.length) break
                    start = (end - SOURCE_CHUNK_OVERLAP).coerceAtLeast(start + 1)
                }
            } else {
                if (current.isNotEmpty() && current.length + paragraph.length + 1 > SOURCE_CHUNK_CHARS) flush()
                if (current.isNotEmpty()) current.appendLine()
                current.append(paragraph)
            }
        }
        flush()
        return result
    }

    private fun scoreChunk(text: String, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 0
        val normalized = normalize(text)
        var score = 0
        tokens.forEach { token ->
            val occurrences = Regex("\\b${Regex.escape(token)}\\b").findAll(normalized).count()
            if (occurrences > 0) score += 8 + (occurrences.coerceAtMost(4) * 2)
        }
        if (tokens.size >= 2) {
            val pairs = tokens.zipWithNext().count { (a, b) -> normalized.contains("$a $b") }
            score += pairs * 10
        }
        return score
    }

    private fun queryTokens(value: String): List<String> = normalize(value)
        .split(Regex("\\s+"))
        .filter { it.length >= 3 && it !in SOURCE_STOP_WORDS }
        .distinct()

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun isSimpleDefinition(question: String): Boolean {
        val n = normalize(question)
        return n.length <= 110 && listOf(
            "que es ", "que significa ", "define ", "definicion de ", "explica el ", "explica la "
        ).any(n::startsWith)
    }

    private fun outputCharBudget(question: String): Int {
        if (isStudyArtifactRequest(question)) return Int.MAX_VALUE
        val n = normalize(question)
        val explicitlyBrief = isResponseTransformRequest(question) || listOf(
            "brevemente", "respuesta breve", "responde breve", "una frase", "en una frase",
            "solo una frase", "muy corto", "muy breve"
        ).any(n::contains)
        if (explicitlyBrief) return 420

        val explicitlyDetailed = listOf(
            "profundiza", "profundizar", "detalladamente", "con detalle", "desarrolla",
            "desarrollalo", "explicacion completa", "explicacion profunda", "amplia", "a fondo"
        ).any(n::contains)
        if (explicitlyDetailed) return 5_200
        if (isBroadSourceRequest(question)) return 6_500
        if (isSourceOverviewRequest(question)) return 4_200
        if (isSimpleDefinition(question)) return 760

        return when (preferences.aiDetail.lowercase()) {
            "breve" -> 900
            "profundo" -> 4_200
            else -> 1_800
        }
    }

    private fun shouldStopGeneration(output: StringBuilder, softLimit: Int): Boolean {
        if (softLimit == Int.MAX_VALUE || output.length < softLimit) return false
        val tail = output.takeLast(140).trimEnd()
        val last = tail.lastOrNull()
        val sentenceBoundary = last == '.' || last == '!' || last == '?'
        return sentenceBoundary || output.length >= softLimit + OUTPUT_HARD_MARGIN_CHARS
    }

    private fun finalizeBoundedOutput(raw: String, softLimit: Int): String {
        val cleaned = cleanModelText(raw).trim()
        if (softLimit == Int.MAX_VALUE || cleaned.length <= softLimit) return cleaned
        val last = cleaned.lastOrNull()
        if (last == '.' || last == '!' || last == '?') return cleaned

        val candidate = cleaned.take((softLimit + OUTPUT_HARD_MARGIN_CHARS).coerceAtMost(cleaned.length))
        val sentenceEnd = maxOf(candidate.lastIndexOf('.'), candidate.lastIndexOf('!'), candidate.lastIndexOf('?'))
        return if (sentenceEnd >= softLimit / 2) candidate.substring(0, sentenceEnd + 1).trim() else candidate.trim()
    }

    private fun responseLengthInstruction(question: String): String {
        val n = normalize(question)
        val explicitlyBrief = isResponseTransformRequest(question) || listOf(
            "brevemente", "respuesta breve", "responde breve", "una frase", "en una frase",
            "solo una frase", "muy corto", "muy breve"
        ).any(n::contains)
        if (explicitlyBrief) return "Extensión: responde en 1–3 frases, sin introducción ni repetición."

        val explicitlyDetailed = listOf(
            "profundiza", "profundizar", "detalladamente", "con detalle", "desarrolla",
            "desarrollalo", "explicacion completa", "explicacion profunda", "amplia", "a fondo"
        ).any(n::contains)
        if (explicitlyDetailed) return "Extensión: desarrolla todo lo necesario con profundidad, estructura y ejemplos cuando ayuden. No recortes una explicación útil por ser larga."

        if (isBroadSourceRequest(question)) return "Extensión: desarrolla el recurso o resumen con la amplitud necesaria para cubrir bien el material, evitando solo la repetición."
        if (isSimpleDefinition(question)) return "Extensión: responde en 1–2 párrafos breves (aprox. 50–110 palabras), sin apartados numerados salvo que se pidan. Define primero el término y añade solo la distinción o contexto esencial. Aunque el nivel general sea Profundo, no conviertas una definición puntual en un ensayo si el estudiante no pidió profundizar."
        if (isSourceOverviewRequest(question)) return "Extensión: ofrece una explicación completa y proporcionada a la fuente; no la reduzcas artificialmente."

        return when (preferences.aiDetail.lowercase()) {
            "breve" -> "Extensión preferida: breve y directa. Resuelve la pregunta con pocas frases o párrafos, salvo que el usuario pida más."
            "profundo" -> "Extensión preferida: profunda. Desarrolla conceptos, relaciones, matices y ejemplos hasta que el tema quede bien explicado, sin repetición innecesaria."
            else -> "Extensión preferida: equilibrada. En una pregunta normal responde en 2–5 párrafos breves y detente cuando quede resuelta; amplía solo si la complejidad o la petición lo exige."
        }
    }

    private fun generationTimeoutMs(question: String): Long {
        val n = normalize(question)
        if (isStudyArtifactRequest(question)) return 300_000L
        val brief = isResponseTransformRequest(question) || listOf("brevemente", "una frase", "muy breve").any(n::contains)
        if (brief) return 90_000L
        val heavy = isBroadSourceRequest(question) || listOf(
            "profundiza", "desarrolla", "con detalle", "mapa", "cuestionario", "tarjetas"
        ).any(n::contains)
        if (heavy || preferences.aiDetail.equals("Profundo", ignoreCase = true)) return 220_000L
        if (preferences.aiDetail.equals("Breve", ignoreCase = true)) return 110_000L
        return 160_000L
    }

    private fun recoverUsefulPartial(raw: String): String? {
        val text = raw.trim()
        if (text.length < MIN_USEFUL_PARTIAL_CHARS) return null
        if (looksLikeStudyArtifact(text)) return text
        val lastSentence = maxOf(text.lastIndexOf('.'), text.lastIndexOf('!'), text.lastIndexOf('?'))
        return if (lastSentence >= MIN_USEFUL_PARTIAL_CHARS - 1) {
            text.substring(0, lastSentence + 1).trim()
        } else {
            text
        }
    }

    private fun looksLikeStudyArtifact(value: String): Boolean =
        value.contains("NOTCAN_MAP", ignoreCase = true) ||
            value.contains("NOTCAN_FLASHCARDS", ignoreCase = true) ||
            value.contains("NOTCAN_QUIZ", ignoreCase = true) ||
            (value.trimStart().startsWith("{") && listOf("\"nodes\"", "\"cards\"", "\"questions\"").any(value::contains))

    private fun isStudyArtifactRequest(question: String): Boolean {
        val n = normalize(question)
        return listOf(
            "mapa mental", "mapa conceptual", "mapa de ideas", "mind map", "concept map",
            "tarjetas didacticas", "flashcards", "cuestionario", "quiz"
        ).any(n::contains)
    }

    private fun isResponseTransformRequest(question: String): Boolean {
        val n = normalize(question)
        if (n.length > 140) return false
        return listOf(
            "resume en una frase", "resumelo", "resumela", "hazlo mas breve", "mas breve",
            "hazlo mas corto", "mas corto", "en una frase", "dilo en una frase", "simplificalo",
            "mas sencillo", "explicalo mas sencillo", "dilo de otra forma", "reformula"
        ).any(n::contains)
    }

    private fun isBroadSourceRequest(question: String): Boolean {
        val n = normalize(question)
        if (listOf(
                "ideas principales", "explica la clase", "explicame la clase", "panorama general",
                "mapa mental", "mapa conceptual", "mapa de ideas", "tarjetas didacticas", "cuestionario"
            ).any(n::contains)) {
            return true
        }
        val summaryIntent = listOf("resume", "resumen", "resumir", "sintesis", "sintetiza").any(n::contains)
        val broadTarget = listOf(
            "la clase", "esta clase", "clase completa", "todo el tema", "tema completo",
            "todos los apuntes", "los apuntes", "la transcripcion", "transcripcion completa",
            "todo el material", "material completo"
        ).any(n::contains)
        return summaryIntent && broadTarget
    }

    private fun isSourceOverviewRequest(question: String): Boolean {
        val n = normalize(question)
        return listOf(
            "de que habla", "de que trata", "que trata", "resumen de la fuente",
            "resume la fuente", "resume el documento", "resume el archivo"
        ).any(n::contains)
    }

    private fun evenlySample(chunks: List<SourceChunk>, limit: Int): List<SourceChunk> {
        if (chunks.size <= limit) return chunks
        if (limit <= 1) return listOf(chunks.first())
        val last = chunks.lastIndex.toDouble()
        return (0 until limit)
            .map { index -> ((last * index) / (limit - 1)).toInt() }
            .distinct()
            .map(chunks::get)
    }

    private fun isTheologicalPrecisionQuery(question: String): Boolean {
        val n = normalize(question)
        return listOf(
            "hipostasis", "hypostasis", "ousia", "trinidad", "trinitario", "trinitaria",
            "persona divina", "naturaleza divina", "cristologia", "cristologico", "cristologica",
            "encarnacion", "verbo", "consubstancial", "consustancial"
        ).any(n::contains)
    }

    private fun buildAdaptiveSystemInstruction(
        strictSources: Boolean,
        pedagogicalMode: Boolean,
        question: String
    ): String {
        if (!isSimpleDefinition(question)) return buildSystemInstruction(strictSources, pedagogicalMode)

        return buildString {
            appendLine("Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.")
            appendLine("Responde en español claro, natural y preciso. Para una definición puntual responde en 1–2 párrafos breves y detente.")
            appendLine("No inventes citas, páginas, autores, fechas ni referencias. No muestres razonamiento interno.")
            appendLine("Usa Markdown simple y no uses LaTeX salvo que el estudiante lo pida.")
            if (preferences.aiInstructions.isNotBlank()) {
                appendLine("Preferencias del estudiante: ${preferences.aiInstructions}")
            }
            if (isTheologicalPrecisionQuery(question)) {
                appendLine("En teología católica usa terminología patrística, trinitaria y cristológica con precisión.")
                appendLine("En la formulación trinitaria madura no presentes hipóstasis como sinónimo de ousia: una única ousia o naturaleza divina y tres hipóstasis o Personas realmente distintas y consustanciales.")
                appendLine("Si mencionas que hypostasis y ousia tuvieron usos históricos solapados, indícalo explícitamente como una cuestión histórica de terminología y no como equivalencia doctrinal trinitaria.")
                appendLine("En cristología: Jesucristo es una sola Persona o hipóstasis, la del Verbo, en dos naturalezas, divina y humana, sin confusión ni división.")
            }
            if (pedagogicalMode) {
                appendLine("Explica de manera pedagógica sin convertir una definición breve en un ensayo.")
            }
            if (strictSources) {
                appendLine("Está activado Solo mis fuentes: no añadas conocimiento externo al material suministrado.")
            }
        }.trim()
    }

    private fun buildSystemInstruction(strictSources: Boolean, pedagogicalMode: Boolean): String = buildString {
        appendLine("Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.")
        appendLine("Responde en español claro, natural, preciso y útil para estudiar.")
        appendLine("Adapta la extensión a lo que se pregunta. Las definiciones y preguntas puntuales deben ser breves por defecto, incluso si el nivel general es Profundo; amplía solo cuando el estudiante lo pida. Las tareas de desarrollo, síntesis o estudio amplio sí pueden ser extensas.")
        appendLine("Responde siempre a la pregunta actual; no repitas una respuesta anterior si ya no corresponde al tema preguntado.")
        appendLine("Nivel de detalle preferido: ${preferences.aiDetail}.")
        if (preferences.aiInstructions.isNotBlank()) {
            appendLine("Preferencias del estudiante: ${preferences.aiInstructions}")
        }
        appendLine("No inventes citas, páginas, autores, fechas ni referencias.")
        appendLine("Usa Markdown simple compatible con la interfaz. No uses LaTeX, delimitadores $...$, \\(...\\), \\[...\\] ni comandos como \\text{} salvo que el estudiante pida expresamente notación matemática; escribe términos y símbolos en texto normal siempre que sea posible.")
        appendLine("No muestres cadena de pensamiento ni razonamiento interno; entrega directamente la respuesta útil.")
        appendLine("En teología católica distingue enseñanza oficial, disciplina, opinión teológica e interpretación académica.")
        appendLine("En terminología patrística, trinitaria y cristológica conserva con rigor las distinciones entre naturaleza/esencia (ousia, physis), hipóstasis/persona y prosopon; no identifiques sin más hipóstasis o persona con esencia o naturaleza. Si una equivalencia es discutida o depende del autor/época, indícalo con prudencia.")
        appendLine("En teología trinitaria católica no describas al Padre, al Hijo y al Espíritu Santo como tres modos, manifestaciones o formas en que se presenta una sola persona. Formula con precisión: una única esencia o naturaleza divina (ousia) y tres Personas o hipóstasis realmente distintas y consustanciales; la distinción personal no divide la esencia divina.")
        appendLine("Cuando expliques hipóstasis, distingue sus usos filosófico/patrístico, trinitario y cristológico. En cristología, Jesucristo es una sola Persona o hipóstasis, la del Verbo, en dos naturalezas, divina y humana, sin confusión ni división.")
        if (pedagogicalMode) {
            appendLine("Actúa como pedagogo académico: ayuda a comprender, planificar, priorizar, practicar recuperación activa y elegir métodos de estudio concretos.")
            appendLine("No actúes como psicólogo ni hagas diagnósticos clínicos; mantente en el terreno del aprendizaje y la organización académica.")
        }
        appendLine("Cuando una afirmación doctrinal sea dudosa, formula con prudencia y no la presentes como cita magisterial.")
        if (strictSources) {
            appendLine("Está activado Solo mis fuentes: no añadas conocimiento externo al material suministrado.")
        }
    }.trim()

    private fun scheduleIdleRelease() {
        idleReleaseJob?.cancel()
        idleReleaseJob = engineScope.launch {
            delay(ENGINE_IDLE_RELEASE_MS)
            mutex.withLock { resetEngine() }
        }
    }

    private suspend fun resetEngine() {
        withContext(NonCancellable + Dispatchers.IO) {
            val old = holder
            holder = null
            runCatching { old?.engine?.close() }
        }
    }

    private fun speculativeDecodingSupported(modelPath: String): Boolean {
        val model = modelManager.modelFile()
        val fingerprint = "${model.length()}:${model.lastModified()}"
        val cachedFingerprint = runtimePrefs.getString(KEY_CAPABILITIES_FINGERPRINT, null)
        if (
            cachedFingerprint == fingerprint &&
            runtimePrefs.contains(KEY_SPECULATIVE_DECODING_SUPPORTED)
        ) {
            return runtimePrefs.getBoolean(KEY_SPECULATIVE_DECODING_SUPPORTED, false)
        }

        var supported = false
        val probeSucceeded = runCatching {
            Capabilities(modelPath).use { capabilities ->
                supported = capabilities.hasSpeculativeDecodingSupport()
            }
        }.isSuccess
        if (probeSucceeded) {
            runtimePrefs.edit()
                .putString(KEY_CAPABILITIES_FINGERPRINT, fingerprint)
                .putBoolean(KEY_SPECULATIVE_DECODING_SUPPORTED, supported)
                .apply()
        }
        return supported
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun ensureEngineReady(): EngineHolder {
        holder?.let { return it }
        return withContext(Dispatchers.IO) {
            holder?.let { return@withContext it }

            val modelPath = modelManager.modelFile().absolutePath
            val gpuAttempt = runCatching {
                val canUseSpeculative = speculativeDecodingSupported(modelPath)
                ExperimentalFlags.enableSpeculativeDecoding = canUseSpeculative
                try {
                    createEngine(modelPath, Backend.GPU()).also { it.initialize() }
                } finally {
                    ExperimentalFlags.enableSpeculativeDecoding = false
                }
            }

            gpuAttempt.fold(
                onSuccess = { EngineHolder(it, "GPU").also { ready -> holder = ready } },
                onFailure = { error ->
                    performanceMetrics.recordGemmaFallback("GPU no pudo iniciar: ${error.javaClass.simpleName}")
                    ensureCpuEngineReady("CPU respaldo")
                }
            )
        }
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun ensureCpuEngineReady(label: String): EngineHolder = withContext(Dispatchers.IO) {
        holder?.takeIf { it.backendLabel.startsWith("CPU") }?.let { return@withContext it }
        ExperimentalFlags.enableSpeculativeDecoding = false
        val modelPath = modelManager.modelFile().absolutePath
        val cpuEngine = runCatching {
            createEngine(modelPath, Backend.CPU()).also { it.initialize() }
        }.getOrElse { cpuError ->
            throw IllegalStateException(
                "LiteRT-LM no pudo iniciar Gemma 4 en CPU: ${cpuError.message ?: cpuError.javaClass.simpleName}",
                cpuError
            )
        }
        EngineHolder(cpuEngine, label).also { holder = it }
    }

    private fun createEngine(modelPath: String, backend: Backend): Engine = Engine(
        EngineConfig(
            modelPath = modelPath,
            backend = backend,
            maxNumTokens = MAX_ENGINE_TOKENS,
            cacheDir = GemmaRuntimeCache.directory(appContext).absolutePath
        )
    )

    companion object {
        const val MODEL_LABEL = "Gemma 4 E2B · LiteRT-LM"
        private const val MAX_FOLLOW_UP_CHARS = 2_400
        private const val MAX_WEB_CONTEXT_CHARS = 4_200
        private const val MAX_VOCAB_CONTEXT_CHARS = 700
        private const val MAX_BROAD_SOURCE_CHARS = 6_500
        private const val MAX_ARTIFACT_SOURCE_CHARS = 4_800
        private const val MAX_OVERVIEW_SOURCE_CHARS = 4_200
        private const val MAX_SIMPLE_DEFINITION_SOURCE_CHARS = 700
        private const val MAX_FOCUSED_SOURCE_CHARS = 1_600
        private const val SOURCE_CHUNK_CHARS = 800
        private const val SOURCE_CHUNK_OVERLAP = 120
        private const val BROAD_SELECTED_CHUNKS = 6
        private const val ARTIFACT_SELECTED_CHUNKS = 5
        private const val OVERVIEW_SELECTED_CHUNKS = 4
        private const val SIMPLE_DEFINITION_SELECTED_CHUNKS = 1
        private const val FOCUSED_SELECTED_CHUNKS = 2
        private const val MAX_ENGINE_TOKENS = 4_096
        private const val TOP_K = 30
        private const val TOP_P = 0.80
        private const val TEMPERATURE = 0.30
        private const val STRUCTURED_TEMPERATURE = 0.12
        private const val GPU_FIRST_TOKEN_TIMEOUT_MS = 30_000L
        private const val ENGINE_IDLE_RELEASE_MS = 10L * 60L * 1_000L
        private const val MIN_USEFUL_PARTIAL_CHARS = 180
        private const val OUTPUT_HARD_MARGIN_CHARS = 240
        private const val KEY_CAPABILITIES_FINGERPRINT = "capabilities_fingerprint"
        private const val KEY_SPECULATIVE_DECODING_SUPPORTED = "speculative_decoding_supported"

        private val SOURCE_STOP_WORDS = setOf(
            "para", "como", "una", "uno", "unos", "unas", "que", "con", "por", "del", "las", "los",
            "esta", "este", "esa", "ese", "sobre", "material", "fuente", "fuentes", "estudio", "segun",
            "desde", "entre", "tambien", "donde", "habla", "dice", "tema", "documento", "archivo"
        )
    }
}
