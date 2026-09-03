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
import com.notcan.app.settings.NotCanPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
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
    private val mutex = Mutex()

    private data class EngineHolder(val engine: Engine, val backendLabel: String)
    private data class SourceChunk(val label: String, val text: String, val score: Int = 0)

    private class GenerationException(
        val backendLabel: String,
        val elapsedMs: Long,
        val generatedChars: Int,
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

    suspend fun answer(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String,
        strictSources: Boolean,
        onPartial: ((text: String, backendLabel: String) -> Unit)? = null
    ): Answer = mutex.withLock {
        check(isAvailable()) { "Gemma 4 LiteRT-LM no está instalado" }

        val subjectKey = subjectName.orEmpty()
        val followUpContext = lastAnswerText
            .takeIf {
                it.isNotBlank() &&
                    lastAnswerSubject == subjectKey &&
                    isResponseTransformRequest(question)
            }
            .orEmpty()

        val sourceContext = if (followUpContext.isNotBlank()) {
            ""
        } else {
            buildSourceContext(subjectName, notes, transcript, question, strictSources)
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
            appendLine()
            appendLine("Pregunta actual del estudiante:")
            append(question.trim())
        }

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(buildSystemInstruction(strictSources)),
            samplerConfig = SamplerConfig(
                topK = TOP_K,
                topP = TOP_P,
                temperature = TEMPERATURE
            )
        )

        val primaryHolder = ensureEngineReady()
        val answer = try {
            generate(primaryHolder, prompt, conversationConfig, onPartial)
        } catch (t: GenerationException) {
            if (primaryHolder.backendLabel == "GPU" && t.generatedChars == 0) {
                resetEngine()
                val cpuHolder = ensureCpuEngineReady("CPU respaldo")
                generate(cpuHolder, prompt, conversationConfig, onPartial)
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
        answer
    }

    private suspend fun generate(
        engineHolder: EngineHolder,
        prompt: String,
        conversationConfig: ConversationConfig,
        onPartial: ((text: String, backendLabel: String) -> Unit)?
    ): Answer = coroutineScope {
        val output = StringBuilder()
        val generationStartedAt = SystemClock.elapsedRealtime()
        try {
            withTimeout(GENERATION_TIMEOUT_MS) {
                engineHolder.engine.createConversation(conversationConfig).use { conversation ->
                    val messages = conversation.sendMessageAsync(prompt).produceIn(this)
                    val firstMessage = if (engineHolder.backendLabel == "GPU") {
                        withTimeout(GPU_FIRST_TOKEN_TIMEOUT_MS) {
                            messages.receiveCatching().getOrNull()
                        }
                    } else {
                        messages.receiveCatching().getOrNull()
                    }

                    firstMessage?.toString()?.takeIf { it.isNotEmpty() }?.let { delta ->
                        output.append(delta)
                        onPartial?.invoke(output.toString(), engineHolder.backendLabel)
                    }

                    for (message in messages) {
                        val delta = message.toString()
                        if (delta.isNotEmpty()) {
                            output.append(delta)
                            onPartial?.invoke(output.toString(), engineHolder.backendLabel)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            throw GenerationException(
                backendLabel = engineHolder.backendLabel,
                elapsedMs = SystemClock.elapsedRealtime() - generationStartedAt,
                generatedChars = output.length,
                cause = t
            )
        }

        val text = output.toString().trim()
        if (text.isBlank()) {
            throw GenerationException(
                backendLabel = engineHolder.backendLabel,
                elapsedMs = SystemClock.elapsedRealtime() - generationStartedAt,
                generatedChars = 0,
                cause = IllegalStateException("Gemma 4 no produjo texto utilizable")
            )
        }
        Answer(text = text, backendLabel = engineHolder.backendLabel)
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
        val broadRequest = isBroadSourceRequest(question)
        val sourceOverviewRequest = isSourceOverviewRequest(question)
        val scored = chunks.map { chunk ->
            chunk.copy(score = scoreChunk(chunk.text, tokens))
        }

        val selected = when {
            broadRequest -> evenlySample(scored, BROAD_SELECTED_CHUNKS)
            sourceOverviewRequest && tokens.isNotEmpty() -> scored
                .filter { it.score > 0 }
                .sortedByDescending { it.score }
                .take(OVERVIEW_SELECTED_CHUNKS)
            tokens.isNotEmpty() -> scored
                .filter { it.score > 0 }
                .sortedByDescending { it.score }
                .take(FOCUSED_SELECTED_CHUNKS)
            else -> emptyList()
        }.ifEmpty {
            // A strict-source question may be a paraphrase with no exact lexical hit.
            // Keep a small representative fallback instead of paying the cost of the whole class.
            if (strictSources) evenlySample(scored, FOCUSED_SELECTED_CHUNKS) else emptyList()
        }

        if (selected.isEmpty()) return ""
        val sourceCharLimit = when {
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
        if (listOf("ideas principales", "explica la clase", "explicame la clase", "panorama general").any(n::contains)) {
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

    private fun buildSystemInstruction(strictSources: Boolean): String = buildString {
        appendLine("Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.")
        appendLine("Responde en español claro, natural, preciso y útil para estudiar.")
        appendLine("Responde siempre a la pregunta actual; no repitas una respuesta anterior si ya no corresponde al tema preguntado.")
        appendLine("Nivel de detalle preferido: ${preferences.aiDetail}.")
        if (preferences.aiInstructions.isNotBlank()) {
            appendLine("Preferencias del estudiante: ${preferences.aiInstructions}")
        }
        appendLine("No inventes citas, páginas, autores, fechas ni referencias.")
        appendLine("No muestres cadena de pensamiento ni razonamiento interno; entrega directamente la respuesta útil.")
        appendLine("En teología católica distingue enseñanza oficial, disciplina, opinión teológica e interpretación académica.")
        appendLine("Cuando una afirmación doctrinal sea dudosa, formula con prudencia y no la presentes como cita magisterial.")
        if (strictSources) {
            appendLine("Está activado Solo mis fuentes: no añadas conocimiento externo al material suministrado.")
        }
    }.trim()

    private suspend fun resetEngine() {
        withContext(NonCancellable + Dispatchers.IO) {
            val old = holder
            holder = null
            runCatching { old?.engine?.close() }
        }
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun ensureEngineReady(): EngineHolder {
        holder?.let { return it }
        return withContext(Dispatchers.IO) {
            holder?.let { return@withContext it }

            val modelPath = modelManager.modelFile().absolutePath
            val gpuAttempt = runCatching {
                var canUseSpeculative = false
                runCatching {
                    Capabilities(modelPath).use { capabilities ->
                        canUseSpeculative = capabilities.hasSpeculativeDecodingSupport()
                    }
                }
                ExperimentalFlags.enableSpeculativeDecoding = canUseSpeculative
                try {
                    createEngine(modelPath, Backend.GPU()).also { it.initialize() }
                } finally {
                    ExperimentalFlags.enableSpeculativeDecoding = false
                }
            }

            gpuAttempt.fold(
                onSuccess = { EngineHolder(it, "GPU").also { ready -> holder = ready } },
                onFailure = { ensureCpuEngineReady("CPU respaldo") }
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
            cacheDir = appContext.cacheDir.absolutePath
        )
    )

    companion object {
        const val MODEL_LABEL = "Gemma 4 E2B · LiteRT-LM"
        private const val MAX_FOLLOW_UP_CHARS = 2_400
        private const val MAX_BROAD_SOURCE_CHARS = 8_500
        private const val MAX_OVERVIEW_SOURCE_CHARS = 5_500
        private const val MAX_FOCUSED_SOURCE_CHARS = 3_400
        private const val SOURCE_CHUNK_CHARS = 1_000
        private const val SOURCE_CHUNK_OVERLAP = 160
        private const val BROAD_SELECTED_CHUNKS = 7
        private const val OVERVIEW_SELECTED_CHUNKS = 5
        private const val FOCUSED_SELECTED_CHUNKS = 3
        private const val MAX_ENGINE_TOKENS = 4_096
        private const val TOP_K = 30
        private const val TOP_P = 0.80
        private const val TEMPERATURE = 0.30
        private const val GPU_FIRST_TOKEN_TIMEOUT_MS = 20_000L
        private const val GENERATION_TIMEOUT_MS = 75_000L

        private val SOURCE_STOP_WORDS = setOf(
            "para", "como", "una", "uno", "unos", "unas", "que", "con", "por", "del", "las", "los",
            "esta", "este", "esa", "ese", "sobre", "material", "fuente", "fuentes", "estudio", "segun",
            "desde", "entre", "tambien", "donde", "habla", "dice", "tema", "documento", "archivo"
        )
    }
}
