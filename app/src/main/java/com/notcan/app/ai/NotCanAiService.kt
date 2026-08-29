package com.notcan.app.ai

import android.content.Context
import android.text.Html
import com.notcan.app.settings.NotCanPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online academic assistant backed by the user's own Mistral Agent.
 * The API key is stored locally with Android Keystore encryption and is never committed to the APK.
 */
class NotCanAiService(private val context: Context) {
    private val appContext = context.applicationContext
    private val preferences = NotCanPreferences(appContext)
    private val credentials = MistralCredentialsStore(appContext)

    fun isConfigured(): Boolean = credentials.hasApiKey() && preferences.mistralAgentId.isNotBlank()

    fun startNewConversation() {
        preferences.mistralConversationId = ""
    }

    suspend fun studyAssistant(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String
    ): String {
        require(isConfigured()) {
            "Configura tu API key y Agent ID de Mistral en Configuración → Asistente NotCan."
        }

        val strictSources = question.contains(SOURCE_ONLY_MARKER)
        val socraticMode = question.contains(SOCRATIC_MARKER)
        val cleanQuestion = question
            .replace(SOURCE_ONLY_MARKER, "")
            .replace(SOCRATIC_MARKER, "")
            .trim()

        val plainNotes = sourcePlainText(notes)
        val plainTranscript = sourcePlainText(transcript)

        if (strictSources && plainNotes.isBlank() && plainTranscript.isBlank()) {
            return "No hay apuntes ni transcripciones disponibles para responder en modo Solo mis fuentes."
        }

        val sourceText = buildString {
            subjectName?.takeIf { it.isNotBlank() }?.let { appendLine("MATERIA: $it") }
            if (plainNotes.isNotBlank()) {
                appendLine("\n[FUENTE: APUNTES]")
                appendLine(plainNotes.takeLast(MAX_SOURCE_CHARS / 2))
            }
            if (plainTranscript.isNotBlank()) {
                appendLine("\n[FUENTE: TRANSCRIPCIÓN]")
                appendLine(plainTranscript.takeLast(MAX_SOURCE_CHARS / 2))
            }
        }.takeLast(MAX_SOURCE_CHARS)

        val prompt = buildString {
            appendLine("CONTEXTO DE NOTCAN")
            appendLine("Nivel de detalle preferido: ${preferences.aiDetail}.")
            if (preferences.aiInstructions.isNotBlank()) appendLine("Preferencias del usuario: ${preferences.aiInstructions}")
            appendLine("No muestres cadena de pensamiento, reflexiones internas ni monólogos. Entrega directamente el resultado útil.")
            appendLine("No inventes citas, páginas, autores, fechas, referencias ni afirmaciones ausentes de las fuentes.")
            appendLine("Si el usuario indica que puede haber un error, no inventes una corrección: corrige solo cuando tengas fundamento suficiente.")

            if (strictSources) {
                appendLine("MODO SOLO MIS FUENTES ACTIVADO.")
                appendLine("Usa exclusivamente el material incluido debajo. Si el dato no consta, responde: 'No consta en las fuentes disponibles'.")
                appendLine("Distingue [Apuntes], [Transcripción] o [Apuntes + Transcripción] cuando atribuyas afirmaciones importantes.")
            } else {
                appendLine("Puedes complementar con conocimiento general, pero distingue con claridad lo aportado por las fuentes del usuario.")
            }

            if (socraticMode) {
                appendLine("MODO SOCRÁTICO ACTIVADO.")
                appendLine("Evalúa brevemente la respuesta del estudiante y termina con UNA sola pregunta concreta y progresiva.")
                appendLine("No reveles de inmediato una solución completa si puede alcanzarse mediante preguntas guiadas.")
            }

            if (sourceText.isNotBlank()) {
                appendLine("\n--- FUENTES DE NOTCAN ---")
                appendLine(sourceText)
                appendLine("--- FIN DE FUENTES ---\n")
            }

            appendLine("SOLICITUD DEL USUARIO:")
            append(cleanQuestion)
        }

        return sendToMistral(prompt)
    }

    private fun sendToMistral(prompt: String): String {
        val apiKey = credentials.apiKey()
        val agentId = preferences.mistralAgentId.trim()
        val existingConversation = preferences.mistralConversationId.trim()

        val response = if (existingConversation.isBlank()) {
            startConversation(apiKey, agentId, prompt)
        } else {
            runCatching { appendConversation(apiKey, existingConversation, prompt) }
                .getOrElse {
                    preferences.mistralConversationId = ""
                    startConversation(apiKey, agentId, prompt)
                }
        }

        response.optString("conversation_id").takeIf { it.isNotBlank() }?.let {
            preferences.mistralConversationId = it
        }

        extractAssistantText(response)?.let { return it }
        extractFunctionCall(response)?.let { call ->
            return "El agente solicitó la función ${call.first}${call.second?.let { " con $it" } ?: ""}. La función fue detectada por NotCan, pero su ejecutor externo todavía no está conectado."
        }
        return "Mistral respondió sin contenido de texto. Vuelve a intentarlo o inicia una conversación nueva."
    }

    private fun startConversation(apiKey: String, agentId: String, prompt: String): JSONObject {
        val body = JSONObject()
            .put("agent_id", agentId)
            .put("inputs", prompt)
            .put("store", true)
        return postJson("$BASE_URL/v1/conversations", apiKey, body)
    }

    private fun appendConversation(apiKey: String, conversationId: String, prompt: String): JSONObject {
        val body = JSONObject()
            .put("inputs", prompt)
            .put("store", true)
        return postJson("$BASE_URL/v1/conversations/$conversationId", apiKey, body)
    }

    private fun postJson(endpoint: String, apiKey: String, body: JSONObject): JSONObject {
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: text.take(500).ifBlank { "HTTP $code" }
                throw IllegalStateException("Mistral ($code): $message")
            }
            JSONObject(text)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractAssistantText(root: JSONObject): String? {
        val outputs = root.optJSONArray("outputs") ?: return null
        val parts = mutableListOf<String>()
        for (i in 0 until outputs.length()) {
            val output = outputs.optJSONObject(i) ?: continue
            val type = output.optString("type")
            if (type.isNotBlank() && type != "message.output") continue
            when (val content = output.opt("content")) {
                is String -> if (content.isNotBlank()) parts += content
                is JSONArray -> {
                    for (j in 0 until content.length()) {
                        when (val chunk = content.opt(j)) {
                            is String -> if (chunk.isNotBlank()) parts += chunk
                            is JSONObject -> {
                                val text = chunk.optString("text").ifBlank { chunk.optString("content") }
                                if (text.isNotBlank()) parts += text
                            }
                        }
                    }
                }
            }
        }
        return parts.joinToString("\n").trim().ifBlank { null }
    }

    private fun extractFunctionCall(root: JSONObject): Pair<String, String?>? {
        val outputs = root.optJSONArray("outputs") ?: return null
        for (i in 0 until outputs.length()) {
            val output = outputs.optJSONObject(i) ?: continue
            val type = output.optString("type")
            if (!type.contains("function", ignoreCase = true)) continue
            val name = output.optString("name")
                .ifBlank { output.optJSONObject("function")?.optString("name").orEmpty() }
                .ifBlank { "función externa" }
            val arguments = output.opt("arguments")?.toString()
                ?: output.optJSONObject("function")?.opt("arguments")?.toString()
            return name to arguments
        }
        return null
    }

    private fun sourcePlainText(value: String): String {
        if (value.isBlank()) return ""
        val withoutScripts = value
            .replace(Regex("(?is)<script.*?>.*?</script>"), "")
            .replace(Regex("(?is)<style.*?>.*?</style>"), "")
        return if (withoutScripts.contains('<') && withoutScripts.contains('>')) {
            Html.fromHtml(withoutScripts, Html.FROM_HTML_MODE_LEGACY).toString().trim()
        } else withoutScripts.trim()
    }

    companion object {
        const val TEXT_MODEL = "Mistral Agent"
        const val SOURCE_ONLY_MARKER = "[SOLO_FUENTES]"
        const val SOCRATIC_MARKER = "[MODO_SOCRATICO]"
        private const val BASE_URL = "https://api.mistral.ai"
        private const val MAX_SOURCE_CHARS = 28_000
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 90_000
    }
}
