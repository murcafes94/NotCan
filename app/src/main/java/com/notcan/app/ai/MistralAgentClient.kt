package com.notcan.app.ai

import android.content.Context
import com.notcan.app.ai.harness.ModelProviderHealthRegistry
import com.notcan.app.ai.harness.ModelProviderId
import com.notcan.app.ai.harness.ProviderException
import com.notcan.app.ai.harness.ProviderFailureClassifier
import com.notcan.app.settings.NotCanPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transport adapter for the current Mistral Agent/Conversations API.
 *
 * Keeping provider transport here prevents NotCanAiService from accumulating HTTP/auth/parsing
 * details. Calls run on Dispatchers.IO and use a small circuit breaker so a broken network/provider
 * quickly falls back to Gemma/local instead of repeatedly blocking the student.
 */
class MistralAgentClient(context: Context) {
    private val appContext = context.applicationContext
    private val credentials = MistralCredentialsStore(appContext)
    private val preferences = NotCanPreferences(appContext)
    private val breaker = ModelProviderHealthRegistry.breaker(ModelProviderId.MISTRAL_AGENT)

    fun isConfigured(): Boolean =
        credentials.hasApiKey() && preferences.mistralAgentId.isNotBlank()

    fun startNewConversation() {
        preferences.mistralConversationId = ""
    }

    suspend fun send(prompt: String): String = withContext(Dispatchers.IO) {
        breaker.beforeCall(ModelProviderId.MISTRAL_AGENT)
        try {
            val response = sendInternal(prompt)
            breaker.recordSuccess()
            response
        } catch (t: Throwable) {
            val providerError = ProviderFailureClassifier.fromThrowable(ModelProviderId.MISTRAL_AGENT, t)
            breaker.recordFailure(providerError)
            throw providerError
        }
    }

    private fun sendInternal(prompt: String): String {
        val apiKey = credentials.apiKey()
        val agentId = preferences.mistralAgentId.trim()
        if (apiKey.isBlank() || agentId.isBlank()) {
            throw ProviderException(
                provider = ModelProviderId.MISTRAL_AGENT,
                kind = com.notcan.app.ai.harness.ProviderFailureKind.AUTHENTICATION,
                message = "Mistral no está configurado.",
                retryable = false
            )
        }

        val existingConversation = preferences.mistralConversationId.trim()
        val response = if (existingConversation.isBlank()) {
            startConversation(apiKey, agentId, prompt)
        } else {
            try {
                appendConversation(apiKey, existingConversation, prompt)
            } catch (error: ProviderException) {
                if (!isStaleConversation(error)) throw error
                preferences.mistralConversationId = ""
                startConversation(apiKey, agentId, prompt)
            }
        }

        response.optString("conversation_id").takeIf { it.isNotBlank() }?.let {
            preferences.mistralConversationId = it
        }

        extractAssistantText(response)?.let { return it }
        extractFunctionCall(response)?.let { call ->
            return "El agente solicitó la función ${call.first}${call.second?.let { " con $it" } ?: ""}. " +
                "La función fue detectada por NotCan, pero su ejecutor externo todavía no está conectado."
        }
        return "Mistral respondió sin contenido de texto. Vuelve a intentarlo o inicia una conversación nueva."
    }

    private fun isStaleConversation(error: ProviderException): Boolean =
        error.statusCode in setOf(404, 409, 410, 422)

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
                val remoteMessage = runCatching {
                    val payload = JSONObject(text)
                    payload.optString("message")
                        .ifBlank { payload.optString("detail") }
                        .ifBlank { payload.optJSONObject("error")?.optString("message").orEmpty() }
                }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: text.take(500).ifBlank { "HTTP $code" }
                throw ProviderFailureClassifier.fromHttp(
                    provider = ModelProviderId.MISTRAL_AGENT,
                    statusCode = code,
                    message = "Mistral ($code): $remoteMessage"
                )
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

    companion object {
        private const val BASE_URL = "https://api.mistral.ai"
        private const val CONNECT_TIMEOUT_MS = 12_000
        private const val READ_TIMEOUT_MS = 90_000
    }
}
