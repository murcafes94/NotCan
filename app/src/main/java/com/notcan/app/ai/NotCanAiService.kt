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

        val preferences = NotCanPreferences(context)
        val systemPrompt = buildString {
            appendLine("Eres ${preferences.assistantName}, el asistente académico local de NotCan.")
            appendLine("Trabajas completamente offline y debes priorizar el material del estudiante.")
            appendLine("Nivel de detalle: ${preferences.aiDetail}.")
            if (preferences.aiInstructions.isNotBlank()) appendLine("Preferencias: ${preferences.aiInstructions}")
            appendLine("No inventes instrucciones del profesor, fechas, tareas ni contenidos ausentes de las fuentes.")
            appendLine("Distingue con claridad hechos presentes en las fuentes de inferencias o conocimiento general.")
            appendLine("Responde en español salvo que el usuario pida otro idioma.")
        }

        val sourceText = buildString {
            subjectName?.let { appendLine("MATERIA: $it") }
            if (notes.isNotBlank()) {
                appendLine("\nAPUNTES DEL ESTUDIANTE:")
                appendLine(notes.takeLast(MAX_SOURCE_CHARS / 2))
            }
            if (transcript.isNotBlank()) {
                appendLine("\nTRANSCRIPCIÓN DE CLASE:")
                appendLine(transcript.takeLast(MAX_SOURCE_CHARS / 2))
            }
        }.takeLast(MAX_SOURCE_CHARS)

        val userPrompt = buildString {
            if (sourceText.isNotBlank()) {
                appendLine(sourceText)
                appendLine("\n--- FIN DE FUENTES ---\n")
            }
            appendLine("SOLICITUD:")
            append(question)
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
        private const val MAX_SOURCE_CHARS = 22_000
        private const val MAX_OUTPUT_TOKENS = 900
    }
}
