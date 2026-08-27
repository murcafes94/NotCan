package com.notcan.app.ai

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.notcan.app.localai.StudyModelManager
import com.notcan.app.localai.StudyModelSpec
import com.notcan.app.localai.StudyModelState
import com.notcan.app.settings.NotCanPreferences
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * Fully local study assistant. No API key, cloud inference or token billing is used.
 * The GGUF model is downloaded separately and executed by the pinned llama.cpp Android runtime.
 */
class NotCanAiService(private val context: Context) {
    private val modelManager = StudyModelManager(context)

    fun isConfigured(): Boolean = modelManager.state() == StudyModelState.INSTALLED

    suspend fun studyAssistant(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String
    ): String {
        require(isConfigured()) {
            "Descarga primero ${StudyModelSpec.DISPLAY_NAME} desde IA → Modelos."
        }

        val strictSources = question.contains(SOURCE_ONLY_MARKER)
        val socraticMode = question.contains(SOCRATIC_MARKER)
        val cleanQuestion = question
            .replace(SOURCE_ONLY_MARKER, "")
            .replace(SOCRATIC_MARKER, "")
            .trim()

        val preferences = NotCanPreferences(context)
        val systemPrompt = buildString {
            appendLine("Eres ${preferences.assistantName}, el asistente académico local de NotCan.")
            appendLine("Trabajas completamente offline y debes priorizar el material del estudiante.")
            appendLine("Nivel de detalle: ${preferences.aiDetail}.")
            if (preferences.aiInstructions.isNotBlank()) appendLine("Preferencias: ${preferences.aiInstructions}")
            appendLine("No inventes instrucciones del profesor, fechas, tareas ni contenidos ausentes de las fuentes.")
            appendLine("Distingue con claridad hechos presentes en las fuentes de inferencias o conocimiento general.")
            appendLine("Responde en español salvo que el usuario pida otro idioma.")
            appendLine("Las fuentes se entregan como [FUENTE: APUNTES] y [FUENTE: TRANSCRIPCIÓN].")

            if (strictSources) {
                appendLine("MODO SOLO FUENTES ACTIVADO.")
                appendLine("Usa exclusivamente los apuntes y la transcripción suministrados; no completes huecos con conocimiento general ni memoria del modelo.")
                appendLine("Si un dato solicitado no aparece en las fuentes, dilo claramente: 'No consta en las fuentes disponibles'.")
                appendLine("Al final de cada párrafo factual indica [Apuntes], [Transcripción] o [Apuntes + Transcripción], según corresponda.")
                appendLine("Antes de entregar la respuesta, comprueba que cada afirmación factual esté respaldada por el material. No muestres ese proceso de comprobación.")
            }

            if (socraticMode) {
                appendLine("MODO SOCRÁTICO ACTIVADO.")
                appendLine("No des una exposición completa ni reveles directamente la solución si el estudiante puede llegar a ella mediante preguntas.")
                appendLine("Evalúa brevemente la respuesta del estudiante, corrige solo lo imprescindible y termina con UNA sola pregunta siguiente, concreta y progresiva.")
                appendLine("Si es el primer turno, formula UNA pregunta diagnóstica basada en las fuentes.")
            }
        }

        val sourceText = buildString {
            subjectName?.let { appendLine("MATERIA: $it") }
            if (notes.isNotBlank()) {
                appendLine("\n[FUENTE: APUNTES]")
                appendLine(notes.takeLast(MAX_SOURCE_CHARS / 2))
            }
            if (transcript.isNotBlank()) {
                appendLine("\n[FUENTE: TRANSCRIPCIÓN]")
                appendLine(transcript.takeLast(MAX_SOURCE_CHARS / 2))
            }
        }.takeLast(MAX_SOURCE_CHARS)

        if (strictSources && sourceText.isBlank()) {
            return "No hay apuntes ni transcripciones disponibles para responder en modo Solo fuentes."
        }

        val userPrompt = buildString {
            if (sourceText.isNotBlank()) {
                appendLine(sourceText)
                appendLine("\n--- FIN DE FUENTES ---\n")
            }
            appendLine("SOLICITUD:")
            append(cleanQuestion)
        }

        return generate(systemPrompt, userPrompt)
    }

    private suspend fun generate(systemPrompt: String, userPrompt: String): String {
        val engine = AiChat.getInferenceEngine(context.applicationContext)
        prepareEngine(engine)

        return try {
            engine.loadModel(modelManager.modelFile().absolutePath)
            engine.setSystemPrompt(systemPrompt)
            val answer = StringBuilder()
            engine.sendUserPrompt(userPrompt, MAX_OUTPUT_TOKENS).collect { token -> answer.append(token) }
            answer.toString().trim().ifBlank { "El modelo local no devolvió texto." }
        } finally {
            runCatching {
                when (engine.state.value) {
                    is InferenceEngine.State.ModelReady,
                    is InferenceEngine.State.Error -> engine.cleanUp()
                    else -> Unit
                }
            }
        }
    }

    private suspend fun prepareEngine(engine: InferenceEngine) {
        when (engine.state.value) {
            is InferenceEngine.State.ModelReady,
            is InferenceEngine.State.Error -> runCatching { engine.cleanUp() }
            else -> Unit
        }

        if (engine.state.value is InferenceEngine.State.Uninitialized ||
            engine.state.value is InferenceEngine.State.Initializing
        ) {
            val state = withTimeout(30_000L) {
                engine.state.first {
                    it is InferenceEngine.State.Initialized || it is InferenceEngine.State.Error
                }
            }
            if (state is InferenceEngine.State.Error) throw state.exception
        }

        check(engine.state.value is InferenceEngine.State.Initialized) {
            "El motor local no está listo (${engine.state.value.javaClass.simpleName})."
        }
    }

    companion object {
        const val TEXT_MODEL = StudyModelSpec.MODEL_NAME
        const val SOURCE_ONLY_MARKER = "[SOLO_FUENTES]"
        const val SOCRATIC_MARKER = "[MODO_SOCRATICO]"
        private const val MAX_SOURCE_CHARS = 22_000
        private const val MAX_OUTPUT_TOKENS = 900
    }
}
