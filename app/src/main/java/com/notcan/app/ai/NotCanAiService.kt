package com.notcan.app.ai

import android.content.Context
import android.text.Html
import com.notcan.app.settings.NotCanPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asistente académico online con fallback local. Si Mistral no está disponible,
 * TuNot sigue pudiendo recuperar material guardado y generar mapas sin Internet.
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
        val mapRequest = OfflineTuNotEngine.isMapRequest(cleanQuestion)

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
            appendLine("TuNot es un tutor académico católico orientado principalmente a teología, filosofía, Sagrada Escritura y derecho canónico.")
            appendLine("Su objetivo es ayudar a estudiar con rigor, fidelidad doctrinal y claridad pedagógica; no debe responder como un asistente religioso genérico.")
            appendLine("Nivel de detalle preferido: ${preferences.aiDetail}.")
            if (preferences.aiInstructions.isNotBlank()) appendLine("Preferencias del usuario: ${preferences.aiInstructions}")
            appendLine("No muestres cadena de pensamiento, reflexiones internas ni monólogos. Entrega directamente el resultado útil.")
            appendLine("No inventes citas, páginas, autores, fechas, referencias ni afirmaciones ausentes de las fuentes.")
            appendLine("Si el usuario indica que puede haber un error, no inventes una corrección: corrige solo cuando tengas fundamento suficiente.")
            appendLine("Cuando una cuestión sea doctrinal, distingue con precisión entre: enseñanza oficial de la Iglesia, disciplina eclesiástica vigente, opinión teológica e interpretación académica.")
            appendLine("Si existe tensión entre una formulación secundaria y una fuente oficial de la Iglesia, da prioridad a la fuente oficial.")
            appendLine("Para respuestas normales usa Markdown simple y limpio: títulos breves, listas con guion, negrita para conceptos clave y párrafos separados. Evita tablas salvo que sean imprescindibles.")
            appendLine("No abuses de comillas, asteriscos ni encabezados. La respuesta debe verse como apuntes bien editados, no como texto técnico del modelo.")
            appendLine()
            appendLine(TuNotCatholicSourcePolicy.promptPolicy())

            if (strictSources) {
                appendLine("MODO SOLO MIS FUENTES ACTIVADO.")
                appendLine("Usa exclusivamente el material incluido debajo. Si el dato no consta, responde: 'No consta en las fuentes disponibles'.")
                appendLine("Distingue [Apuntes], [Transcripción] o [Apuntes + Transcripción] cuando atribuyas afirmaciones importantes.")
                appendLine("En este modo no complementes con web, biblioteca base ni conocimiento general salvo que el usuario lo pida explícitamente después.")
            } else {
                appendLine("Puedes complementar el material de clase con conocimiento católico general y, cuando esté disponible, con biblioteca base o búsqueda web católica autorizada.")
                appendLine("Prioridad de contexto: 1) fuentes de la clase, 2) biblioteca católica base, 3) fuentes oficiales/católicas de la lista blanca, 4) conocimiento general del modelo.")
                appendLine("Si utilizas conocimiento general del modelo sin respaldo documental, no lo presentes como cita ni como declaración magisterial textual.")
            }

            if (socraticMode) {
                appendLine("MODO SOCRÁTICO ACTIVADO.")
                appendLine("Evalúa brevemente la respuesta del estudiante y termina con UNA sola pregunta concreta y progresiva.")
                appendLine("No reveles de inmediato una solución completa si puede alcanzarse mediante preguntas guiadas.")
            }

            if (mapRequest) {
                appendLine("MODO ARTEFACTO MAPA ACTIVADO.")
                appendLine("El usuario quiere un mapa que NotCan renderizará de forma interactiva.")
                appendLine("Devuelve exclusivamente un artefacto entre los marcadores exactos <<<NOTCAN_MAP>>> y <<<END_NOTCAN_MAP>>>.")
                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown y sin comentarios.")
                appendLine("Esquema obligatorio:")
                appendLine("{\"type\":\"mind_map|concept_map\",\"title\":\"...\",\"layout\":\"horizontal|radial|radial_cards|ideas|tree|constellation\",\"root_node_id\":\"root\",\"nodes\":[{\"id\":\"root\",\"title\":\"...\",\"description\":\"...\",\"level\":0,\"source_refs\":[\"Apuntes\"]}],\"edges\":[{\"from\":\"root\",\"to\":\"n1\",\"label\":\"...\"}]}")
                appendLine("Para mapa mental académico usa por defecto layout horizontal.")
                appendLine("Usa radial_cards cuando el usuario pida algo más visual, presentable o con tarjetas explicativas.")
                appendLine("Usa ideas cuando el usuario pida mapa de ideas, estilo creativo, esquema visual sencillo o presentación tipo infografía.")
                appendLine("Para mapa conceptual usa tree cuando predomine la jerarquía y añade etiquetas breves y semánticas en edges.")
                appendLine("El contenido y el layout son independientes: no sacrifiques relaciones académicas por decorar el mapa.")
                appendLine("Genera normalmente entre 8 y 16 nodos. Prefiere 4 a 6 ramas principales y como máximo 2 subramas por cada rama.")
                appendLine("Los títulos de nodo deben tener entre 2 y 5 palabras y ser conceptos, no oraciones completas. Intenta no superar 32 caracteres por título.")
                appendLine("Pon las explicaciones en description, no dentro del title. Las descripciones deben ser breves, normalmente menores de 100 caracteres.")
                appendLine("Las etiquetas de relación de edges deben tener entre 1 y 3 palabras. No uses frases largas sobre las líneas.")
                appendLine("Evita ramas redundantes, conceptos casi iguales y nodos que solo repitan el nombre de la fuente.")
                appendLine("Cada nodo debe tener id único y todo edge debe referirse a ids existentes.")
                appendLine("En source_refs usa nombres breves como 'Apuntes' o 'Transcripción' solo cuando el nodo tenga respaldo en esa fuente.")
                appendLine("No incluyas texto antes ni después de los marcadores.")
            }

            if (sourceText.isNotBlank()) {
                appendLine("\n--- FUENTES DE NOTCAN ---")
                appendLine(sourceText)
                appendLine("--- FIN DE FUENTES ---\n")
            }

            appendLine("SOLICITUD DEL USUARIO:")
            append(cleanQuestion)
        }

        return runCatching { sendToMistral(prompt) }
            .getOrElse {
                OfflineTuNotEngine.answer(
                    subjectName = subjectName,
                    notes = plainNotes,
                    transcript = plainTranscript,
                    question = cleanQuestion
                )
            }
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
