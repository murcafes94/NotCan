package com.notcan.app.ai

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.notcan.app.localai.StudyModelManager
import com.notcan.app.localai.StudyModelState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** On-device TuNot brain backed by Qwen2.5 1.5B Instruct Q4_K_M + llama.cpp. */
class LocalQwenTuNotEngine(context: Context) {
    private val appContext = context.applicationContext
    private val modelManager = StudyModelManager(appContext)
    private val mutex = Mutex()

    fun isAvailable(): Boolean = runCatching {
        modelManager.state() == StudyModelState.INSTALLED
    }.getOrDefault(false)

    suspend fun answer(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String,
        strictSources: Boolean
    ): String = mutex.withLock {
        check(isAvailable()) { "Qwen2.5 no está instalado" }
        val engine = AiChat.getInferenceEngine(appContext)
        ensureModelReady(engine)

        val sourceContext = buildString {
            subjectName?.takeIf { it.isNotBlank() }?.let { appendLine("Materia: $it") }
            if (notes.isNotBlank()) {
                appendLine("\n[Apuntes]")
                appendLine(notes.takeLast(MAX_SOURCE_PART_CHARS))
            }
            if (transcript.isNotBlank()) {
                appendLine("\n[Transcripción]")
                appendLine(transcript.takeLast(MAX_SOURCE_PART_CHARS))
            }
        }.takeLast(MAX_SOURCE_CHARS)

        val prompt = buildString {
            if (strictSources) {
                appendLine("Responde SOLO con las fuentes incluidas. Si el dato no consta, dilo claramente.")
            } else {
                appendLine("Usa el material de clase cuando sea pertinente. Puedes explicar con conocimiento general fiable, sin presentarlo como cita de los apuntes.")
            }
            if (sourceContext.isNotBlank()) {
                appendLine("\n--- MATERIAL DE NOTCAN ---")
                appendLine(sourceContext)
                appendLine("--- FIN DEL MATERIAL ---")
            }
            appendLine("\nPregunta del estudiante:")
            append(question.trim())
        }

        val output = StringBuilder()
        withTimeout(GENERATION_TIMEOUT_MS) {
            engine.sendUserPrompt(prompt, PREDICT_TOKENS).collect { token -> output.append(token) }
        }
        sanitizeModelOutput(output.toString())
            .ifBlank { error("Qwen2.5 no produjo texto utilizable") }
    }

    private fun sanitizeModelOutput(raw: String): String = raw
        .replace(SPECIAL_TOKEN_REGEX, "")
        .replace("<|im_start|>", "")
        .replace("<|im_end|>", "")
        .replace("<|endoftext|>", "")
        .trim()

    private suspend fun ensureModelReady(engine: InferenceEngine) {
        var state = withTimeout(INIT_TIMEOUT_MS) {
            engine.state.first {
                it is InferenceEngine.State.Initialized ||
                    it is InferenceEngine.State.ModelReady ||
                    it is InferenceEngine.State.Error
            }
        }
        if (state is InferenceEngine.State.Error) {
            runCatching { engine.cleanUp() }
            state = withTimeout(INIT_TIMEOUT_MS) {
                engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.ModelReady }
            }
        }
        if (state is InferenceEngine.State.ModelReady) return
        engine.loadModel(modelManager.modelFile().absolutePath)
        engine.setSystemPrompt(SYSTEM_PROMPT)
    }

    companion object {
        const val MODEL_LABEL = "Qwen2.5 1.5B · local"
        private const val MAX_SOURCE_PART_CHARS = 3_200
        private const val MAX_SOURCE_CHARS = 6_400
        private const val PREDICT_TOKENS = 512
        private const val INIT_TIMEOUT_MS = 120_000L
        private const val GENERATION_TIMEOUT_MS = 180_000L
        private val SPECIAL_TOKEN_REGEX = Regex("""<\|[^>]+\|>""")
        private val SYSTEM_PROMPT = """
            Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.
            Responde en español claro, natural, preciso y útil para estudiar.
            Prioriza fidelidad a los apuntes y transcripciones proporcionados.
            No inventes citas, páginas, autores, fechas ni referencias.
            Cuando se trabaje solo con fuentes, no añadas información que no esté en ellas.
            Para teología católica, distingue enseñanza oficial, disciplina, opinión teológica e interpretación académica.
            No muestres cadena de pensamiento. Responde directamente con Markdown sencillo.
        """.trimIndent()
    }
}
