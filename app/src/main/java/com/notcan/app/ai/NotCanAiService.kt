package com.notcan.app.ai

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class NotCanAiService(private val context: Context) {

    fun isConfigured(): Boolean =
        FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null

    private fun ensureFirebase() {
        if (!isConfigured()) {
            error("Gemini aún no está configurado. Vincula NotCan con Firebase AI Logic para activar la IA.")
        }
    }

    suspend fun ask(prompt: String): String {
        ensureFirebase()
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(TEXT_MODEL)
        return model.generateContent(prompt).text?.trim().orEmpty()
            .ifBlank { "Gemini no devolvió texto." }
    }

    suspend fun studyAssistant(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String
    ): String {
        val prompt = buildString {
            appendLine("Eres el asistente académico de NotCan. Responde en español claro y preciso.")
            subjectName?.let { appendLine("Materia: $it") }
            if (notes.isNotBlank()) {
                appendLine("\nAPUNTES DEL USUARIO:\n$notes")
            }
            if (transcript.isNotBlank()) {
                appendLine("\nTRANSCRIPCIÓN DE CLASE:\n$transcript")
            }
            appendLine("\nSOLICITUD:\n$question")
            appendLine("No inventes datos que no estén en el material. Señala cuando una respuesta sea una inferencia.")
        }
        return ask(prompt)
    }

    suspend fun transcribeAudio(file: File): String {
        ensureFirebase()
        require(file.exists()) { "El audio local no existe." }
        // Firebase AI Logic inline requests have a 20 MB total request limit. Base64 adds overhead,
        // so we keep a conservative ceiling and rely on live transcription for long classes.
        require(file.length() <= SAFE_INLINE_AUDIO_BYTES) {
            "Este audio es demasiado grande para transcripción final en línea directa. Usa la transcripción en vivo o divide la grabación."
        }
        val bytes = file.readBytes()
        val model = Firebase.ai(backend = GenerativeBackend.googleAI())
            .generativeModel(TEXT_MODEL)
        val prompt = content {
            inlineData(bytes, "audio/m4a")
            text(
                "Transcribe fielmente esta clase en español. Conserva el orden, separa párrafos, " +
                    "marca cambios de hablante cuando sean evidentes y no resumas."
            )
        }
        return model.generateContent(prompt).text?.trim().orEmpty()
            .ifBlank { "No se obtuvo una transcripción." }
    }

    companion object {
        const val TEXT_MODEL = "gemini-3.7-flash"
        private const val SAFE_INLINE_AUDIO_BYTES = 14L * 1024L * 1024L
    }
}

@OptIn(PublicPreviewAPI::class)
class GeminiLiveTranscriber(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onTranscriptChunk: (String) -> Unit,
    private val onStatus: (String) -> Unit = {}
) {
    private var session: LiveSession? = null
    private var receiveJob: Job? = null

    suspend fun start(): Boolean {
        if (session != null) return true
        val configured = FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null
        if (!configured) {
            onStatus("IA sin configurar")
            return false
        }
        return try {
            val liveModel = Firebase.ai(backend = GenerativeBackend.googleAI()).liveModel(
                modelName = "gemini-2.5-flash-native-audio-preview-12-2025",
                generationConfig = liveGenerationConfig {
                    responseModality = ResponseModality.AUDIO
                    inputAudioTranscription = AudioTranscriptionConfig()
                }
            )
            val connected = liveModel.connect()
            session = connected
            receiveJob = scope.launch {
                try {
                    connected.receive().collect { message ->
                        if (message is LiveServerContent) {
                            message.inputTranscription?.text
                                ?.takeIf { it.isNotBlank() }
                                ?.let(onTranscriptChunk)
                        }
                    }
                } catch (t: Throwable) {
                    onStatus("Transcripción en vivo interrumpida: ${t.message ?: "error de red"}")
                }
            }
            onStatus("Transcripción en vivo activa")
            true
        } catch (t: Throwable) {
            onStatus("No se pudo iniciar Gemini Live: ${t.message ?: "error"}")
            false
        }
    }

    suspend fun sendPcm16Khz(bytes: ByteArray) {
        try {
            session?.sendAudioRealtime(InlineData(bytes, "audio/pcm;rate=16000"))
        } catch (t: Throwable) {
            onStatus("Gemini Live sin conexión: ${t.message ?: "error"}")
        }
    }

    suspend fun close() {
        receiveJob?.cancel()
        receiveJob = null
        try { session?.close() } catch (_: Throwable) { }
        session = null
        onStatus("Transcripción en vivo detenida")
    }
}
