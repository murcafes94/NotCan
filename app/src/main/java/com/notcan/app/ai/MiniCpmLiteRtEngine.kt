package com.notcan.app.ai

import android.content.Context
import android.os.SystemClock
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import com.notcan.app.localai.MiniCpmBackend
import com.notcan.app.localai.MiniCpmLiteRtCatalog
import com.notcan.app.localai.MiniCpmModelId
import com.notcan.app.localai.MiniCpmModelManager
import com.notcan.app.localai.MiniCpmModelState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Experimental MiniCPM5 LiteRT-LM runner.
 *
 * This is deliberately not wired into TuNot's production routing yet. It gives the test branch a
 * real, compile-checked inference path that can be exercised from a diagnostic/benchmark screen once
 * a model has been downloaded. The stable Gemma/local fallback remains untouched until MiniCPM has
 * passed device tests and NotCan Bench.
 */
class MiniCpmLiteRtEngine(context: Context) {
    private val appContext = context.applicationContext
    private val modelManager = MiniCpmModelManager(appContext)

    data class CapabilitySnapshot(
        val supportsThinking: Boolean,
        val supportsFunctionCalling: Boolean,
        val textInput: Boolean,
        val visionInput: Boolean,
        val audioInput: Boolean,
        val videoInput: Boolean
    )

    data class GenerationResult(
        val modelId: MiniCpmModelId,
        val backend: MiniCpmBackend,
        val text: String,
        val thought: String,
        val firstTokenMs: Long,
        val totalMs: Long
    )

    fun isInstalled(modelId: MiniCpmModelId): Boolean =
        runCatching { modelManager.state(modelId) == MiniCpmModelState.INSTALLED }.getOrDefault(false)

    fun capabilities(modelId: MiniCpmModelId): CapabilitySnapshot {
        check(isInstalled(modelId)) { "${MiniCpmLiteRtCatalog.spec(modelId).displayName} no está instalado" }
        val path = modelManager.modelFile(modelId).absolutePath
        return Capabilities(path).use { caps ->
            val modalities = caps.inputModalities()
            CapabilitySnapshot(
                supportsThinking = caps.supportsThinking(),
                supportsFunctionCalling = caps.supportsFunctionCalling(),
                textInput = modalities.text,
                visionInput = modalities.vision,
                audioInput = modalities.audio,
                videoInput = modalities.video
            )
        }
    }

    suspend fun generate(
        modelId: MiniCpmModelId,
        prompt: String,
        preferredBackend: MiniCpmBackend? = null,
        systemInstruction: String = DEFAULT_SYSTEM_INSTRUCTION,
        thinkingEnabled: Boolean = false,
        thinkingTokenBudget: Int = DEFAULT_THINKING_BUDGET,
        maxOutputTokens: Int = DEFAULT_MAX_OUTPUT_TOKENS,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        onPartial: ((String) -> Unit)? = null
    ): GenerationResult {
        require(prompt.isNotBlank()) { "El prompt no puede estar vacío" }
        require(maxOutputTokens > 0) { "maxOutputTokens debe ser positivo" }
        require(thinkingTokenBudget > 0) { "thinkingTokenBudget debe ser positivo" }
        require(timeoutMs > 0L) { "timeoutMs debe ser positivo" }
        check(isInstalled(modelId)) { "${MiniCpmLiteRtCatalog.spec(modelId).displayName} no está instalado" }

        val spec = MiniCpmLiteRtCatalog.spec(modelId)
        val backend = chooseBackend(modelId, preferredBackend)
        val modelPath = modelManager.modelFile(modelId).absolutePath
        val cacheDir = File(appContext.cacheDir, "minicpm-litert/${modelId.name.lowercase()}")
            .apply { mkdirs() }

        val engine = withContext(Dispatchers.IO) {
            Engine(
                EngineConfig(
                    modelPath = modelPath,
                    backend = backend.toLiteRt(),
                    cacheDir = cacheDir.absolutePath
                )
            ).also { it.initialize() }
        }

        try {
            val capabilities = Capabilities(modelPath).use { it.supportsThinking() }
            val effectiveThinking = thinkingEnabled && capabilities
            val conversationConfig = ConversationConfig(
                systemInstruction = systemInstruction.takeIf { it.isNotBlank() }?.let(Contents::of),
                samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.95,
                    temperature = 1.0
                ),
                maxOutputToken = maxOutputTokens,
                thinkingConfig = ThinkingConfig(
                    enableThinking = effectiveThinking,
                    thinkingTokenBudget = thinkingTokenBudget
                )
            )

            val startedAt = SystemClock.elapsedRealtime()
            var firstTokenMs = 0L
            val output = StringBuilder()
            val thought = StringBuilder()

            coroutineScope {
                withTimeout(timeoutMs) {
                    engine.createConversation(conversationConfig).use { conversation ->
                        val messages = conversation.sendMessageAsync(prompt).produceIn(this)
                        try {
                            for (message in messages) {
                                val delta = message.toString()
                                if (delta.isNotEmpty()) {
                                    if (firstTokenMs == 0L) {
                                        firstTokenMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
                                    }
                                    output.append(delta)
                                    onPartial?.invoke(output.toString())
                                }
                                message.channels["thought"]?.takeIf { it.isNotEmpty() }?.let(thought::append)
                            }
                        } finally {
                            messages.cancel()
                        }
                    }
                }
            }

            val text = output.toString().trim()
            check(text.isNotBlank()) { "MiniCPM5 no produjo una respuesta utilizable" }
            val totalMs = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
            return GenerationResult(
                modelId = modelId,
                backend = backend,
                text = text,
                thought = thought.toString().trim(),
                firstTokenMs = firstTokenMs.takeIf { it > 0L } ?: totalMs,
                totalMs = totalMs
            )
        } finally {
            withContext(Dispatchers.IO) { runCatching { engine.close() } }
        }
    }

    private fun chooseBackend(
        modelId: MiniCpmModelId,
        preferredBackend: MiniCpmBackend?
    ): MiniCpmBackend {
        val spec = MiniCpmLiteRtCatalog.spec(modelId)
        if (preferredBackend != null) {
            require(preferredBackend in spec.backends) {
                "${spec.displayName} no admite backend ${preferredBackend.name}"
            }
            return preferredBackend
        }
        return when {
            spec.backends.size == 1 -> spec.backends.first()
            MiniCpmBackend.GPU in spec.backends -> MiniCpmBackend.GPU
            else -> MiniCpmBackend.CPU
        }
    }

    private fun MiniCpmBackend.toLiteRt(): Backend = when (this) {
        MiniCpmBackend.CPU -> Backend.CPU()
        MiniCpmBackend.GPU -> Backend.GPU()
    }

    companion object {
        private const val DEFAULT_MAX_OUTPUT_TOKENS = 1024
        private const val DEFAULT_THINKING_BUDGET = 2048
        private const val DEFAULT_TIMEOUT_MS = 180_000L
        private const val DEFAULT_SYSTEM_INSTRUCTION =
            "Eres TuNot, un tutor académico. Responde en español claro, preciso y útil. " +
                "No muestres razonamiento interno ni inventes citas, páginas o referencias."
    }
}
