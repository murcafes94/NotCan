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
    private val webResearch = WebResearchService(appContext)
    private val localLfm = LocalLfmTuNotEngine(appContext)

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
        val strictSources = question.contains(SOURCE_ONLY_MARKER)
        val forcedWeb = question.contains(WEB_SEARCH_MARKER)
        val autoWeb = question.contains(AUTO_WEB_MARKER)
        val socraticMode = question.contains(SOCRATIC_MARKER)
        val flashcardRequest = question.contains(FLASHCARDS_MARKER)
        val quizRequest = question.contains(QUIZ_MARKER)
        val cleanQuestion = question
            .replace(SOURCE_ONLY_MARKER, "")
            .replace(WEB_SEARCH_MARKER, "")
            .replace(AUTO_WEB_MARKER, "")
            .replace(SOCRATIC_MARKER, "")
            .replace(FLASHCARDS_MARKER, "")
            .replace(QUIZ_MARKER, "")
            .trim()
        val localQuestion = buildString {
            append(cleanQuestion)
            if (flashcardRequest) append(" · tarjetas didácticas")
            if (quizRequest) append(" · cuestionario")
        }
        val mapRequest = OfflineTuNotEngine.isMapRequest(cleanQuestion) && !flashcardRequest && !quizRequest
        val lowerQuestion = cleanQuestion.lowercase()
        val conceptualMapRequest = mapRequest && ("conceptual" in lowerQuestion || "concept map" in lowerQuestion)
        val ideaMapRequest = mapRequest && ("mapa de ideas" in lowerQuestion || "lluvia" in lowerQuestion || "brainstorm" in lowerQuestion)

        val plainNotes = sourcePlainText(notes)
        val plainTranscript = sourcePlainText(transcript)
        if (strictSources && plainNotes.isBlank() && plainTranscript.isBlank()) {
            return "No hay apuntes ni transcripciones disponibles para responder en modo Solo mis fuentes."
        }

        suspend fun localFallback(skipLfm: Boolean = false): String {
            val lfmEligible = !mapRequest && !flashcardRequest && !quizRequest
            if (!skipLfm && lfmEligible && localLfm.isAvailable()) {
                try {
                    val answer = localLfm.answer(
                        subjectName = subjectName,
                        notes = plainNotes,
                        transcript = plainTranscript,
                        question = localQuestion,
                        strictSources = strictSources
                    )
                    preferences.lastLfmError = ""
                    return markEngine("LFM2.5 local", answer)
                } catch (t: Throwable) {
                    preferences.lastLfmError = t.message ?: t.javaClass.simpleName
                }
            }
            val basic = OfflineTuNotEngine.answer(
                subjectName = subjectName,
                notes = plainNotes,
                transcript = plainTranscript,
                question = localQuestion
            )
            return markEngine("Local básico", basic)
        }

        when (preferences.aiEnginePreference) {
            "LFM2.5 local" -> return localFallback(skipLfm = false)
            "Local básico" -> return localFallback(skipLfm = true)
        }

        if (!isConfigured()) return localFallback()

        val wantsWeb = !strictSources && (forcedWeb || (autoWeb && WebResearchService.shouldAutoSearch(cleanQuestion)))
        val webResults = if (wantsWeb) {
            runCatching { webResearch.research(cleanQuestion, limit = 5, readTop = 3) }.getOrDefault(emptyList())
        } else emptyList()
        val webContext = webResearch.formatForPrompt(webResults)

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
            if (wantsWeb) {
                appendLine("MODO WEB DE NOTCAN ACTIVADO.")
                appendLine("NotCan realizó la búsqueda fuera de Mistral y te entrega FUENTES WEB reales debajo.")
                appendLine("Usa esas fuentes para datos actuales o externos. No inventes URLs ni atribuciones.")
                appendLine("Distingue claramente información recuperada de la web de conocimiento general o material de clase.")
                appendLine("Al final incluye una sección breve 'Fuentes web' con título y URL de las fuentes que realmente hayas usado.")
                if (webResults.isEmpty()) appendLine("La búsqueda no devolvió resultados utilizables; dilo explícitamente si la respuesta depende de información actual.")
            }

            if (strictSources) {
                appendLine("MODO SOLO MIS FUENTES ACTIVADO.")
                appendLine("Usa exclusivamente el material incluido debajo. Si el dato no consta, responde: 'No consta en las fuentes disponibles'.")
                appendLine("Distingue [Apuntes], [Transcripción] o [Apuntes + Transcripción] cuando atribuyas afirmaciones importantes.")
                appendLine("En este modo no complementes con web, biblioteca base ni conocimiento general salvo que el usuario lo pida explícitamente después.")
            } else {
                appendLine("MODO DE RESPUESTA LIBRE ACTIVADO.")
                appendLine("El material de clase incluido debajo es contexto opcional: úsalo cuando sea pertinente, pero no fuerces la respuesta a salir de apuntes, transcripciones o archivos.")
                appendLine("Si la pregunta es general, conceptual o pide una explicación más amplia, responde con el conocimiento fiable necesario y complementa con el material de clase solo cuando aporte valor.")
                appendLine("No presentes los apuntes del estudiante como autoridad por defecto ni repitas posibles errores de esos apuntes sin advertirlos.")
                appendLine("Cuando uses material de clase, intégralo de forma natural y distingue una cita o atribución solo si realmente procede de ese material.")
                appendLine("Puedes apoyarte en biblioteca católica base, fuentes oficiales/católicas autorizadas y conocimiento general del modelo según la naturaleza de la pregunta.")
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
                appendLine("Antes de construir el JSON identifica conceptos centrales, elimina redundancias, agrupa ideas relacionadas y verifica que cada rama aporte información distinta.")
                appendLine("Devuelve exclusivamente un artefacto entre los marcadores exactos <<<NOTCAN_MAP>>> y <<<END_NOTCAN_MAP>>>.")
                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown y sin comentarios.")
                appendLine("Esquema obligatorio:")
                appendLine("{\"type\":\"mind_map|concept_map\",\"title\":\"...\",\"layout\":\"horizontal|radial|radial_cards|ideas|tree|constellation\",\"root_node_id\":\"root\",\"nodes\":[{\"id\":\"root\",\"title\":\"...\",\"description\":\"...\",\"level\":0,\"source_refs\":[\"Apuntes\"]}],\"edges\":[{\"from\":\"root\",\"to\":\"n1\",\"label\":\"...\"}]}")
                appendLine("Genera normalmente entre 8 y 16 nodos. Prefiere 4 a 6 ramas principales y como máximo 2 subramas por rama.")
                appendLine("Los títulos de nodo deben tener entre 2 y 5 palabras y ser conceptos, no oraciones completas. Intenta no superar 32 caracteres por título.")
                appendLine("Pon las explicaciones en description, no dentro del title. Las descripciones deben ser breves, normalmente menores de 100 caracteres.")
                appendLine("Evita ramas redundantes, conceptos casi iguales, nodos huérfanos y nodos que solo repitan el nombre de la fuente.")
                appendLine("Cada nodo debe tener id único. Todo nodo distinto de la raíz debe quedar conectado por al menos un edge válido.")
                appendLine("En source_refs usa nombres breves como 'Apuntes' o 'Transcripción' solo cuando el nodo tenga respaldo en esa fuente.")

                when {
                    conceptualMapRequest -> {
                        appendLine("TIPO SOLICITADO: MAPA CONCEPTUAL.")
                        appendLine("Usa type concept_map. Prioriza relaciones semánticas sobre decoración.")
                        appendLine("Las etiquetas de edge son obligatorias cuando expresan una relación conceptual: 1 a 3 palabras como 'implica', 'presupone', 'incluye', 'se funda en'.")
                        appendLine("Permite relaciones cruzadas entre conceptos cuando sean académicamente significativas; no fuerces todo a una jerarquía artificial.")
                        appendLine("Usa layout tree por defecto, salvo que otra disposición haga más legibles las relaciones.")
                    }
                    ideaMapRequest -> {
                        appendLine("TIPO SOLICITADO: MAPA DE IDEAS.")
                        appendLine("Usa layout ideas. Presenta ideas breves, distintas y expandibles alrededor del tema central.")
                        appendLine("No conviertas cada idea en un párrafo; deja el detalle en description.")
                    }
                    else -> {
                        appendLine("TIPO SOLICITADO: MAPA MENTAL.")
                        appendLine("Usa type mind_map y layout horizontal por defecto.")
                        appendLine("Debe ser jerárquico: tema central → ramas → subramas. Evita relaciones cruzadas salvo que sean imprescindibles.")
                        appendLine("Los edge labels pueden omitirse si la jerarquía ya expresa claramente la relación.")
                    }
                }
                appendLine("No incluyas texto antes ni después de los marcadores.")
            }

            if (flashcardRequest) {
                appendLine("MODO ARTEFACTO TARJETAS ACTIVADO.")
                appendLine("NotCan mostrará estas tarjetas en una pantalla de repaso, una por una, con pregunta al frente y respuesta al reverso.")
                appendLine("Devuelve exclusivamente un artefacto entre los marcadores exactos <<<NOTCAN_FLASHCARDS>>> y <<<END_NOTCAN_FLASHCARDS>>>.")
                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown, sin comentarios y sin texto adicional.")
                appendLine("Esquema obligatorio:")
                appendLine("{\"title\":\"...\",\"cards\":[{\"question\":\"...\",\"answer\":\"...\",\"source_ref\":\"Apuntes\"}]}")
                appendLine("Genera entre 12 y 20 tarjetas salvo que el material sea claramente insuficiente.")
                appendLine("Cada tarjeta debe ser atómica y evaluable: una sola idea por tarjeta.")
                appendLine("La pregunta debe ser clara, específica y útil para recuperación activa; evita copiar literalmente encabezados como preguntas.")
                appendLine("La respuesta debe ser breve pero suficiente: normalmente una frase o un párrafo corto, no un ensayo.")
                appendLine("Combina definición, relación, causa, consecuencia, comparación y aplicación cuando el contenido lo permita; evita tarjetas repetitivas.")
                appendLine("No uses preguntas ambiguas del tipo '¿Qué dice el texto?' ni respuestas que dependan de ver otra tarjeta.")
                appendLine("source_ref es opcional y debe ser breve. Úsalo solo si puedes atribuir la tarjeta a una fuente disponible.")
                appendLine("No incluyas texto antes ni después de los marcadores.")
            }

            if (quizRequest) {
                appendLine("MODO ARTEFACTO CUESTIONARIO ACTIVADO.")
                appendLine("NotCan presentará una pregunta a la vez, corregirá localmente las preguntas objetivas y permitirá repetir los errores.")
                appendLine("Devuelve exclusivamente un artefacto entre los marcadores exactos <<<NOTCAN_QUIZ>>> y <<<END_NOTCAN_QUIZ>>>.")
                appendLine("Dentro de los marcadores devuelve JSON válido, sin bloque markdown, sin comentarios y sin texto adicional.")
                appendLine("Esquema obligatorio:")
                appendLine("{\"title\":\"...\",\"questions\":[{\"id\":\"q1\",\"type\":\"multiple_choice|true_false|short_answer\",\"question\":\"...\",\"options\":[\"...\"],\"correct_answer\":\"...\",\"explanation\":\"...\",\"source_ref\":\"Apuntes\"}]}")
                appendLine("Genera normalmente entre 12 y 20 preguntas. Si el usuario no especifica tipo, crea un cuestionario mixto con predominio de opción múltiple.")
                appendLine("Para multiple_choice usa exactamente 4 opciones distintas y una sola correcta. correct_answer debe coincidir literalmente con una de options.")
                appendLine("Los distractores deben ser plausibles y del mismo nivel conceptual que la respuesta correcta; evita opciones absurdas o pistas gramaticales.")
                appendLine("No uses 'Todas las anteriores' ni 'Ninguna de las anteriores' salvo que el usuario las solicite expresamente.")
                appendLine("Para true_false usa correct_answer 'Verdadero' o 'Falso' y explica por qué.")
                appendLine("Para short_answer formula una respuesta breve de desarrollo y coloca en correct_answer una respuesta modelo concisa.")
                appendLine("Incluye explanation breve en todas las preguntas para que el error sirva para estudiar.")
                appendLine("Distribuye las preguntas entre conceptos centrales, relaciones, causas/consecuencias, comparaciones y aplicación; no repitas la misma idea con palabras distintas.")
                appendLine("source_ref es opcional y debe ser breve. Úsalo cuando la pregunta dependa directamente de una fuente disponible.")
                appendLine("No incluyas texto antes ni después de los marcadores.")
            }

            if (sourceText.isNotBlank()) {
                appendLine("\n--- MATERIAL DE CLASE DISPONIBLE ---")
                appendLine(sourceText)
                appendLine("--- FIN DEL MATERIAL DE CLASE ---\n")
            }
            if (webContext.isNotBlank()) {
                appendLine("\n--- FUENTES WEB RECUPERADAS POR NOTCAN ---")
                appendLine(webContext)
                appendLine("--- FIN DE FUENTES WEB ---\n")
            }

            appendLine("SOLICITUD DEL USUARIO:")
            append(cleanQuestion)
        }

        return try {
            markEngine("Mistral", sendToMistral(prompt))
        } catch (_: Throwable) {
            localFallback()
        }
    }

    private fun markEngine(engine: String, text: String): String =
        "<<<NOTCAN_ENGINE:${engine.replace(">", "")}>>>\n$text"

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
        const val WEB_SEARCH_MARKER = "[BUSCAR_WEB_NOTCAN]"
        const val AUTO_WEB_MARKER = "[AUTO_WEB_NOTCAN]"
        const val SOCRATIC_MARKER = "[MODO_SOCRATICO]"
        const val FLASHCARDS_MARKER = "[GENERAR_TARJETAS_NOTCAN]"
        const val QUIZ_MARKER = "[GENERAR_CUESTIONARIO_NOTCAN]"
        private const val BASE_URL = "https://api.mistral.ai"
        private const val MAX_SOURCE_CHARS = 28_000
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 90_000
    }
}
