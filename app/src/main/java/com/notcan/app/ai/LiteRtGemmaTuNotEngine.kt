package com.notcan.app.ai

import android.content.Context
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

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

    data class Answer(val text: String, val backendLabel: String)

    @Volatile
    private var holder: EngineHolder? = null

    fun isAvailable(): Boolean = runCatching {
        modelManager.state() == GemmaLiteRtModelState.INSTALLED
    }.getOrDefault(false)

    suspend fun answer(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String,
        strictSources: Boolean
    ): Answer = mutex.withLock {
        check(isAvailable()) { "Gemma 4 LiteRT-LM no está instalado" }

        val engineHolder = ensureEngineReady()
        val sourceContext = buildSourceContext(subjectName, notes, transcript)
        val prompt = buildString {
            if (strictSources) {
                appendLine("Responde únicamente con el material de NotCan incluido abajo.")
                appendLine("Si la respuesta no consta en ese material, responde exactamente: No consta en las fuentes disponibles.")
            } else {
                appendLine("Usa el material de NotCan cuando sea pertinente. Puedes complementar con conocimiento general fiable.")
            }
            if (sourceContext.isNotBlank()) {
                appendLine()
                appendLine("--- MATERIAL DE NOTCAN ---")
                appendLine(sourceContext)
                appendLine("--- FIN DEL MATERIAL ---")
            }
            appendLine()
            appendLine("Pregunta del estudiante:")
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

        val output = StringBuilder()
        withTimeout(GENERATION_TIMEOUT_MS) {
            engineHolder.engine.createConversation(conversationConfig).use { conversation ->
                conversation.sendMessageAsync(prompt).collect { message ->
                    output.append(message.toString())
                }
            }
        }

        val text = output.toString().trim()
        check(text.isNotBlank()) { "Gemma 4 no produjo texto utilizable" }
        Answer(text = text, backendLabel = engineHolder.backendLabel)
    }

    private fun buildSourceContext(subjectName: String?, notes: String, transcript: String): String = buildString {
        subjectName?.takeIf { it.isNotBlank() }?.let { appendLine("Materia: $it") }
        if (notes.isNotBlank()) {
            appendLine("\n[Apuntes]")
            appendLine(notes.takeLast(MAX_NOTES_CHARS))
        }
        if (transcript.isNotBlank()) {
            appendLine("\n[Transcripción]")
            appendLine(transcript.takeLast(MAX_TRANSCRIPT_CHARS))
        }
    }.takeLast(MAX_SOURCE_CHARS)

    private fun buildSystemInstruction(strictSources: Boolean): String = buildString {
        appendLine("Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.")
        appendLine("Responde en español claro, natural, preciso y útil para estudiar.")
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

            val ready = gpuAttempt.fold(
                onSuccess = { EngineHolder(it, "GPU") },
                onFailure = { gpuError ->
                    val cpuEngine = runCatching {
                        createEngine(modelPath, Backend.CPU()).also { it.initialize() }
                    }.getOrElse { cpuError ->
                        throw IllegalStateException(
                            "LiteRT-LM no pudo iniciar Gemma 4. GPU: ${gpuError.message ?: gpuError.javaClass.simpleName}; CPU: ${cpuError.message ?: cpuError.javaClass.simpleName}",
                            cpuError
                        )
                    }
                    EngineHolder(cpuEngine, "CPU")
                }
            )
            holder = ready
            ready
        }
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
        private const val MAX_NOTES_CHARS = 4_000
        private const val MAX_TRANSCRIPT_CHARS = 6_000
        private const val MAX_SOURCE_CHARS = 10_000
        private const val MAX_ENGINE_TOKENS = 4_096
        private const val TOP_K = 30
        private const val TOP_P = 0.80
        private const val TEMPERATURE = 0.30
        private const val GENERATION_TIMEOUT_MS = 120_000L
    }
}
