from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:180]!r}")
    write(path, text.replace(old, new, 1))


def replace_between(path: str, start: str, end: str, new_middle: str) -> None:
    text = read(path)
    a = text.index(start)
    b = text.index(end, a)
    write(path, text[:a] + new_middle + text[b:])


# 1) Remove the obsolete llama.cpp/Qwen runtime from the Android app.
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 43\n        versionName = "0.8.20"\n',
    '        versionCode = 44\n        versionName = "0.8.21"\n'
)
replace_once(
    "app/build.gradle.kts",
    '    implementation(project(":llama-android"))\n',
    ''
)
replace_once(
    "settings.gradle.kts",
    'include(":app")\ninclude(":llama-android")\n',
    'include(":app")\n'
)
qwen = ROOT / "app/src/main/java/com/notcan/app/ai/LocalQwenTuNotEngine.kt"
if qwen.exists():
    qwen.unlink()

# Old engine preferences migrate to Automatic instead of another obsolete model.
replace_once(
    "app/src/main/java/com/notcan/app/settings/NotCanPreferences.kt",
    '            return if (stored == "LFM2.5 local") "Qwen2.5 local" else stored\n',
    '            return if (stored == "LFM2.5 local" || stored == "Qwen2.5 local") "Automático" else stored\n'
)

# 2) Settings: hide old engines, keep a cleanup action, and retain Gemma + emergency basic fallback.
p = "app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt"
replace_once(p, 'import com.notcan.app.localai.StudyModelSpec\n', '')
replace_once(p, 'import com.notcan.app.localai.StudyModelState\n', '')
replace_once(
    p,
    '''    val studyState = remember(refreshTick) {\n        runCatching { studyManager.state() }.getOrDefault(StudyModelState.NOT_INSTALLED)\n    }\n    val studyProgress = remember(refreshTick) {\n        runCatching { studyManager.progressPercent() }.getOrNull()\n    }\n''',
    '''    val oldQwenInstalled = remember(refreshTick) {\n        runCatching {\n            studyManager.modelFile().let { it.exists() && it.length() >= 500_000_000L }\n        }.getOrDefault(false)\n    }\n'''
)
replace_once(
    p,
    '''                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    listOf("Gemma 4 local", "Qwen2.5 local").forEach { option ->\n                        FilterChip(\n                            selected = aiEngine == option,\n                            onClick = { aiEngine = option; preferences.aiEnginePreference = option },\n                            label = { Text(option) }\n                        )\n                    }\n                }\n''',
    '''                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                    FilterChip(\n                        selected = aiEngine == "Gemma 4 local",\n                        onClick = { aiEngine = "Gemma 4 local"; preferences.aiEnginePreference = "Gemma 4 local" },\n                        label = { Text("Gemma 4 local") }\n                    )\n                }\n'''
)
replace_once(
    p,
    '''                if (aiEngine == "Qwen2.5 local" && studyState != StudyModelState.INSTALLED) {\n                    Text("Qwen2.5 todavía no está instalado.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)\n                }\n''',
    ''
)
old_offline = '''                DownloadComponentCard(\n                    title = StudyModelSpec.DISPLAY_NAME,\n                    subtitle = "${StudyModelSpec.MODEL_NAME} · ~1.12 GB · ${StudyModelSpec.LICENSE} · motor anterior de prueba",\n                    stateText = when (studyState) {\n                        StudyModelState.INSTALLED -> "Instalado · disponible para comparar"\n                        StudyModelState.DOWNLOADING -> "Descargando"\n                        StudyModelState.NOT_INSTALLED -> "No instalado"\n                    },\n                    progress = studyProgress,\n                    installed = studyState == StudyModelState.INSTALLED,\n                    downloading = studyState == StudyModelState.DOWNLOADING,\n                    onDownload = {\n                        runCatching { studyManager.enqueueDownload() }\n                            .onFailure { saveMessage = "Qwen2.5: ${it.message ?: "no se pudo iniciar la descarga"}" }\n                        refreshTick++\n                    },\n                    onRemove = { runCatching { studyManager.removeModel() }; refreshTick++ }\n                )\n\n                if (legacyLfmInstalled) {\n                    Card(\n                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),\n                        shape = RoundedCornerShape(14.dp)\n                    ) {\n                        Row(\n                            Modifier.fillMaxWidth().padding(13.dp),\n                            verticalAlignment = Alignment.CenterVertically,\n                            horizontalArrangement = Arrangement.spacedBy(10.dp)\n                        ) {\n                            Column(Modifier.weight(1f)) {\n                                Text("LFM2.5 anterior", color = NotCanOffWhite, fontWeight = FontWeight.Medium)\n                                Text("~731 MB · legado de las pruebas locales anteriores", color = NotCanGray, style = MaterialTheme.typography.bodySmall)\n                            }\n                            OutlinedButton(onClick = {\n                                runCatching { studyManager.removeLegacyLfmModel() }\n                                    .onSuccess { saveMessage = "LFM2.5 anterior eliminado." }\n                                    .onFailure { saveMessage = it.message ?: "No se pudo eliminar LFM2.5" }\n                                refreshTick++\n                            }) { Text("Eliminar") }\n                        }\n                    }\n                }\n'''
new_offline = '''                if (oldQwenInstalled || legacyLfmInstalled) {\n                    Card(\n                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),\n                        shape = RoundedCornerShape(14.dp)\n                    ) {\n                        Row(\n                            Modifier.fillMaxWidth().padding(13.dp),\n                            verticalAlignment = Alignment.CenterVertically,\n                            horizontalArrangement = Arrangement.spacedBy(10.dp)\n                        ) {\n                            Column(Modifier.weight(1f)) {\n                                Text("Modelos locales anteriores", color = NotCanOffWhite, fontWeight = FontWeight.Medium)\n                                Text("Qwen2.5/LFM2.5 ya no se usan. Puedes eliminarlos y recuperar espacio.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)\n                            }\n                            OutlinedButton(onClick = {\n                                val currentRemoved = runCatching { studyManager.removeModel() }.getOrDefault(false)\n                                val lfmRemoved = runCatching { studyManager.removeLegacyLfmModel() }.getOrDefault(false)\n                                saveMessage = if (currentRemoved && lfmRemoved) "Modelos antiguos eliminados." else "Se intentó limpiar los modelos antiguos."\n                                refreshTick++\n                            }) { Text("Liberar espacio") }\n                        }\n                    }\n                }\n'''
replace_once(p, old_offline, new_offline)

# 3) NotCanAiService: Gemma becomes the only generative local engine, gains web + vocabulary + pedagogy + study artifacts.
p = "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
replace_once(p, 'import com.notcan.app.settings.NotCanPreferences\n', 'import com.notcan.app.data.local.NotCanDatabase\nimport com.notcan.app.settings.NotCanPreferences\nimport kotlinx.coroutines.flow.first\n')
replace_once(p, '    private val localQwen = LocalQwenTuNotEngine(appContext)\n', '')
start = '        val strictSources = question.contains(SOURCE_ONLY_MARKER)\n'
end = '        val sourceText = buildString {\n'
new_top = '''        val strictSources = question.contains(SOURCE_ONLY_MARKER)\n        val forcedWeb = question.contains(WEB_SEARCH_MARKER)\n        val autoWeb = question.contains(AUTO_WEB_MARKER)\n        val socraticMode = question.contains(SOCRATIC_MARKER)\n        val pedagogicalMode = question.contains(PEDAGOGY_MARKER)\n        val vocabularyRequested = question.contains(VOCABULARY_MARKER)\n        val flashcardRequest = question.contains(FLASHCARDS_MARKER)\n        val quizRequest = question.contains(QUIZ_MARKER)\n        val cleanQuestion = question\n            .replace(SOURCE_ONLY_MARKER, "")\n            .replace(WEB_SEARCH_MARKER, "")\n            .replace(AUTO_WEB_MARKER, "")\n            .replace(SOCRATIC_MARKER, "")\n            .replace(PEDAGOGY_MARKER, "")\n            .replace(VOCABULARY_MARKER, "")\n            .replace(FLASHCARDS_MARKER, "")\n            .replace(QUIZ_MARKER, "")\n            .trim()\n        val mapRequest = OfflineTuNotEngine.isMapRequest(cleanQuestion) && !flashcardRequest && !quizRequest\n        val lowerQuestion = cleanQuestion.lowercase()\n        val conceptualMapRequest = mapRequest && ("conceptual" in lowerQuestion || "concept map" in lowerQuestion)\n        val ideaMapRequest = mapRequest && ("mapa de ideas" in lowerQuestion || "lluvia" in lowerQuestion || "brainstorm" in lowerQuestion)\n\n        val plainNotes = sourcePlainText(notes)\n        val plainTranscript = sourcePlainText(transcript)\n\n        val wantsWeb = !strictSources && (forcedWeb || (autoWeb && WebResearchService.shouldAutoSearch(cleanQuestion)))\n        val webResults = if (wantsWeb) {\n            runCatching { webResearch.research(cleanQuestion, limit = 5, readTop = 3) }.getOrDefault(emptyList())\n        } else emptyList()\n        val webContext = webResearch.formatForPrompt(webResults)\n        val vocabularyContext = runCatching { loadVocabularyContext(subjectName, vocabularyRequested) }.getOrDefault("")\n\n        if (strictSources && plainNotes.isBlank() && plainTranscript.isBlank() && vocabularyContext.isBlank()) {\n            return "No hay apuntes, transcripciones ni vocabulario académico disponibles para responder en modo Solo mis fuentes."\n        }\n\n        val localQuestion = buildLocalQuestion(\n            cleanQuestion = cleanQuestion,\n            mapRequest = mapRequest,\n            conceptualMapRequest = conceptualMapRequest,\n            ideaMapRequest = ideaMapRequest,\n            flashcardRequest = flashcardRequest,\n            quizRequest = quizRequest,\n            pedagogicalMode = pedagogicalMode\n        )\n\n        suspend fun localFallback(allowGemma: Boolean = true): String {\n            if (allowGemma && localGemma.isAvailable()) {\n                var lastGemmaPartial = ""\n                var lastGemmaBackend = ""\n                try {\n                    val answer = localGemma.answer(\n                        subjectName = subjectName,\n                        notes = plainNotes,\n                        transcript = plainTranscript,\n                        question = localQuestion,\n                        strictSources = strictSources,\n                        intentQuestion = cleanQuestion,\n                        webContext = if (strictSources) "" else webContext,\n                        vocabularyContext = vocabularyContext,\n                        pedagogicalMode = pedagogicalMode,\n                        onPartial = { partialText, backendLabel ->\n                            lastGemmaPartial = partialText\n                            lastGemmaBackend = backendLabel\n                            onPartial?.invoke(markEngine("Gemma 4 local · $backendLabel", partialText))\n                        }\n                    )\n                    preferences.lastLocalAiError = ""\n                    return markEngine("Gemma 4 local · ${answer.backendLabel}", answer.text)\n                } catch (t: Throwable) {\n                    val errorText = t.message ?: t.javaClass.simpleName\n                    if (lastGemmaPartial.trim().length >= MIN_USABLE_GEMMA_PARTIAL_CHARS) {\n                        preferences.lastLocalAiError = "Gemma 4 (respuesta parcial conservada): $errorText"\n                        return markEngine(\n                            "Gemma 4 local · ${lastGemmaBackend.ifBlank { "parcial" }}",\n                            lastGemmaPartial.trim()\n                        )\n                    }\n                    onPartial?.invoke("")\n                    preferences.lastLocalAiError = "Gemma 4: $errorText"\n                }\n            }\n            val basic = OfflineTuNotEngine.answer(\n                subjectName = subjectName,\n                notes = plainNotes,\n                transcript = plainTranscript,\n                question = cleanQuestion\n            )\n            return markEngine("Local básico", basic)\n        }\n\n        when (preferences.aiEnginePreference) {\n            "Gemma 4 local" -> return localFallback(allowGemma = true)\n            "Local básico" -> return localFallback(allowGemma = false)\n        }\n\n        if (!isConfigured()) return localFallback()\n\n'''
replace_between(p, start, end, new_top)

replace_once(
    p,
    '            appendLine(TuNotCatholicSourcePolicy.promptPolicy())\n',
    '''            appendLine(TuNotCatholicSourcePolicy.promptPolicy())\n            if (pedagogicalMode) {\n                appendLine("MODO PEDAGOGO ACADÉMICO ACTIVADO.")\n                appendLine("Ayuda a aprender, planificar, priorizar y elegir técnicas de estudio. Sé práctico y ajusta el plan a la carga del estudiante.")\n                appendLine("No actúes como psicólogo ni hagas diagnósticos clínicos. Si el estudiante expresa cansancio o saturación, responde desde la organización y la pedagogía.")\n            }\n            if (vocabularyRequested) {\n                appendLine("El usuario pidió trabajar con el vocabulario académico de NotCan. Prioriza los términos suministrados y respeta exactamente sus grafías.")\n            }\n'''
)
replace_once(
    p,
    '''            if (webContext.isNotBlank()) {\n                appendLine("\\n--- FUENTES WEB RECUPERADAS POR NOTCAN ---")\n''',
    '''            if (vocabularyContext.isNotBlank()) {\n                appendLine("\\n--- VOCABULARIO ACADÉMICO DE NOTCAN ---")\n                appendLine(vocabularyContext)\n                appendLine("--- FIN DEL VOCABULARIO ---\\n")\n            }\n            if (webContext.isNotBlank()) {\n                appendLine("\\n--- FUENTES WEB RECUPERADAS POR NOTCAN ---")\n'''
)
helper_anchor = '    private fun sourcePlainText(value: String): String {\n'
helpers = r'''    private suspend fun loadVocabularyContext(subjectName: String?, requested: Boolean): String {
        val dao = NotCanDatabase.getInstance(appContext).dao()
        val subjects = dao.getAllSubjects()
        val subject = subjectName?.let { name -> subjects.firstOrNull { it.name.equals(name, ignoreCase = true) } }
        val cycleId = subject?.cycleId ?: dao.getAllCycles().firstOrNull { it.isActive }?.id ?: return ""
        val limit = if (requested) 120 else 60
        val terms = dao.observeVocabularyForCycle(cycleId).first()
            .asSequence()
            .filter { term -> subject == null || term.subjectId == null || term.subjectId == subject.id }
            .distinctBy { it.normalizedTerm }
            .take(limit)
            .toList()
        if (terms.isEmpty()) return ""
        return terms.joinToString(" · ") { term ->
            buildString {
                append(term.term)
                if (term.area.isNotBlank() && term.area != "general") append(" [${term.area}]")
            }
        }
    }

    private fun buildLocalQuestion(
        cleanQuestion: String,
        mapRequest: Boolean,
        conceptualMapRequest: Boolean,
        ideaMapRequest: Boolean,
        flashcardRequest: Boolean,
        quizRequest: Boolean,
        pedagogicalMode: Boolean
    ): String = buildString {
        appendLine(cleanQuestion)
        if (pedagogicalMode) {
            appendLine()
            appendLine("Actúa como pedagogo académico de NotCan: ayuda a comprender, organizar el estudio, priorizar y elegir técnicas concretas. No hagas diagnósticos psicológicos.")
        }
        if (mapRequest) {
            appendLine()
            appendLine("Devuelve exclusivamente un mapa entre <<<NOTCAN_MAP>>> y <<<END_NOTCAN_MAP>>> con JSON válido y sin markdown.")
            appendLine("Esquema: {\"type\":\"mind_map|concept_map\",\"title\":\"...\",\"layout\":\"horizontal|radial|radial_cards|ideas|tree|constellation\",\"root_node_id\":\"root\",\"nodes\":[{\"id\":\"root\",\"title\":\"...\",\"description\":\"...\",\"level\":0,\"source_refs\":[\"Apuntes\"]}],\"edges\":[{\"from\":\"root\",\"to\":\"n1\",\"label\":\"...\"}]}")
            appendLine("Genera 8–16 nodos claros, sin redundancias, todos conectados.")
            when {
                conceptualMapRequest -> appendLine("Usa type concept_map y prioriza relaciones semánticas etiquetadas.")
                ideaMapRequest -> appendLine("Usa layout ideas y tarjetas breves alrededor del tema central.")
                else -> appendLine("Usa type mind_map y una jerarquía tema central → ramas → subramas.")
            }
        }
        if (flashcardRequest) {
            appendLine()
            appendLine("Devuelve exclusivamente entre <<<NOTCAN_FLASHCARDS>>> y <<<END_NOTCAN_FLASHCARDS>>> un JSON válido: {\"title\":\"...\",\"cards\":[{\"question\":\"...\",\"answer\":\"...\",\"source_ref\":\"Apuntes\"}]}")
            appendLine("Genera 12–20 tarjetas atómicas, claras y útiles para recuperación activa.")
        }
        if (quizRequest) {
            appendLine()
            appendLine("Devuelve exclusivamente entre <<<NOTCAN_QUIZ>>> y <<<END_NOTCAN_QUIZ>>> un JSON válido: {\"title\":\"...\",\"questions\":[{\"id\":\"q1\",\"type\":\"multiple_choice|true_false|short_answer\",\"question\":\"...\",\"options\":[\"...\"],\"correct_answer\":\"...\",\"explanation\":\"...\",\"source_ref\":\"Apuntes\"}]}")
            appendLine("Genera 12–20 preguntas. En opción múltiple usa 4 opciones y una sola respuesta correcta literal.")
        }
    }

'''
replace_once(p, helper_anchor, helpers + helper_anchor)
replace_once(
    p,
    '        const val SOCRATIC_MARKER = "[MODO_SOCRATICO]"\n',
    '        const val SOCRATIC_MARKER = "[MODO_SOCRATICO]"\n        const val PEDAGOGY_MARKER = "[MODO_PEDAGOGO_NOTCAN]"\n        const val VOCABULARY_MARKER = "[VOCABULARIO_NOTCAN]"\n'
)

# 4) Gemma engine: adaptive depth, longer stable timeouts, web/vocabulary context and pedagogy instructions.
p = "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt"
replace_once(
    p,
    '''        question: String,\n        strictSources: Boolean,\n        onPartial: ((text: String, backendLabel: String) -> Unit)? = null\n''',
    '''        question: String,\n        strictSources: Boolean,\n        intentQuestion: String = question,\n        webContext: String = "",\n        vocabularyContext: String = "",\n        pedagogicalMode: Boolean = false,\n        onPartial: ((text: String, backendLabel: String) -> Unit)? = null\n'''
)
replace_once(p, '                    isResponseTransformRequest(question)\n', '                    isResponseTransformRequest(intentQuestion)\n')
replace_once(p, '            buildSourceContext(subjectName, notes, transcript, question, strictSources)\n', '            buildSourceContext(subjectName, notes, transcript, intentQuestion, strictSources)\n')
replace_once(
    p,
    '''            } else if (sourceContext.isNotBlank()) {\n                appendLine()\n                appendLine("--- MATERIAL DE NOTCAN RELEVANTE PARA ESTA PREGUNTA ---")\n                appendLine(sourceContext)\n                appendLine("--- FIN DEL MATERIAL ---")\n            }\n            appendLine()\n            appendLine(responseLengthInstruction(question))\n''',
    '''            } else if (sourceContext.isNotBlank()) {\n                appendLine()\n                appendLine("--- MATERIAL DE NOTCAN RELEVANTE PARA ESTA PREGUNTA ---")\n                appendLine(sourceContext)\n                appendLine("--- FIN DEL MATERIAL ---")\n            }\n            if (vocabularyContext.isNotBlank()) {\n                appendLine()\n                appendLine("--- VOCABULARIO ACADÉMICO DE NOTCAN ---")\n                appendLine(vocabularyContext.take(MAX_VOCAB_CONTEXT_CHARS))\n                appendLine("Estos términos sirven para reconocer grafías y terminología; por sí solos no son definiciones ni prueba doctrinal.")\n                appendLine("--- FIN DEL VOCABULARIO ---")\n            }\n            if (!strictSources && webContext.isNotBlank()) {\n                appendLine()\n                appendLine("--- FUENTES WEB RECUPERADAS POR NOTCAN ---")\n                appendLine(webContext.take(MAX_WEB_CONTEXT_CHARS))\n                appendLine("Usa solo URLs y datos presentes aquí. Si citas web, menciona título/URL sin inventarlos.")\n                appendLine("--- FIN DE FUENTES WEB ---")\n            }\n            appendLine()\n            appendLine(responseLengthInstruction(intentQuestion))\n'''
)
replace_once(p, '            systemInstruction = Contents.of(buildSystemInstruction(strictSources)),\n', '            systemInstruction = Contents.of(buildSystemInstruction(strictSources, pedagogicalMode)),\n')
replace_once(
    p,
    '''        val primaryHolder = ensureEngineReady()\n        val answer = try {\n            generate(primaryHolder, prompt, conversationConfig, onPartial)\n''',
    '''        val generationTimeoutMs = generationTimeoutMs(intentQuestion)\n        val primaryHolder = ensureEngineReady()\n        val answer = try {\n            generate(primaryHolder, prompt, conversationConfig, generationTimeoutMs, onPartial)\n'''
)
replace_once(p, '                generate(cpuHolder, prompt, conversationConfig, onPartial)\n', '                generate(cpuHolder, prompt, conversationConfig, generationTimeoutMs, onPartial)\n')
replace_once(
    p,
    '''        prompt: String,\n        conversationConfig: ConversationConfig,\n        onPartial: ((text: String, backendLabel: String) -> Unit)?\n''',
    '''        prompt: String,\n        conversationConfig: ConversationConfig,\n        timeoutMs: Long,\n        onPartial: ((text: String, backendLabel: String) -> Unit)?\n'''
)
replace_once(p, '            withTimeout(GENERATION_TIMEOUT_MS) {\n', '            withTimeout(timeoutMs) {\n')

# Make study artifact requests broad-context requests too.
replace_once(
    p,
    '''        if (listOf("ideas principales", "explica la clase", "explicame la clase", "panorama general").any(n::contains)) {\n            return true\n        }\n''',
    '''        if (listOf(\n                "ideas principales", "explica la clase", "explicame la clase", "panorama general",\n                "mapa mental", "mapa conceptual", "mapa de ideas", "tarjetas didacticas", "cuestionario"\n            ).any(n::contains)) {\n            return true\n        }\n'''
)

# Replace response length policy with preference-aware adaptive depth.
start = '    private fun responseLengthInstruction(question: String): String {\n'
end = '    private fun recoverUsefulPartial(raw: String): String? {\n'
new_policy = r'''    private fun responseLengthInstruction(question: String): String {
        val n = normalize(question)
        val explicitlyBrief = isResponseTransformRequest(question) || listOf(
            "brevemente", "respuesta breve", "responde breve", "una frase", "en una frase",
            "solo una frase", "muy corto", "muy breve"
        ).any(n::contains)
        if (explicitlyBrief) return "Extensión: responde en 1–3 frases, sin introducción ni repetición."

        val explicitlyDetailed = listOf(
            "profundiza", "profundizar", "detalladamente", "con detalle", "desarrolla",
            "desarrollalo", "explicacion completa", "explicacion profunda", "amplia", "a fondo"
        ).any(n::contains)
        if (explicitlyDetailed) return "Extensión: desarrolla todo lo necesario con profundidad, estructura y ejemplos cuando ayuden. No recortes una explicación útil por ser larga."

        if (isBroadSourceRequest(question)) return "Extensión: desarrolla el recurso o resumen con la amplitud necesaria para cubrir bien el material, evitando solo la repetición."
        if (isSourceOverviewRequest(question)) return "Extensión: ofrece una explicación completa y proporcionada a la fuente; no la reduzcas artificialmente."

        return when (preferences.aiDetail.lowercase()) {
            "breve" -> "Extensión preferida: breve y directa. Resuelve la pregunta con pocas frases o párrafos, salvo que el usuario pida más."
            "profundo" -> "Extensión preferida: profunda. Desarrolla conceptos, relaciones, matices y ejemplos hasta que el tema quede bien explicado, sin repetición innecesaria."
            else -> "Extensión preferida: equilibrada. Usa toda la extensión necesaria para explicar bien; sé breve en preguntas simples y desarrolla las complejas."
        }
    }

    private fun generationTimeoutMs(question: String): Long {
        val n = normalize(question)
        val brief = isResponseTransformRequest(question) || listOf("brevemente", "una frase", "muy breve").any(n::contains)
        if (brief) return 90_000L
        val heavy = isBroadSourceRequest(question) || listOf(
            "profundiza", "desarrolla", "con detalle", "mapa", "cuestionario", "tarjetas"
        ).any(n::contains)
        if (heavy || preferences.aiDetail.equals("Profundo", ignoreCase = true)) return 220_000L
        if (preferences.aiDetail.equals("Breve", ignoreCase = true)) return 110_000L
        return 160_000L
    }

'''
replace_between(p, start, end, new_policy)
replace_once(
    p,
    '    private fun buildSystemInstruction(strictSources: Boolean): String = buildString {\n',
    '    private fun buildSystemInstruction(strictSources: Boolean, pedagogicalMode: Boolean): String = buildString {\n'
)
replace_once(
    p,
    '''        appendLine("Sé conciso por defecto: responde solo con la extensión necesaria y evita repetir la misma idea.")\n        appendLine("Responde siempre a la pregunta actual; no repitas una respuesta anterior si ya no corresponde al tema preguntado.")\n        appendLine("Sé conciso por defecto: responde lo necesario para resolver la pregunta y detente. Amplía solo si el estudiante lo pide o si la tarea exige un resumen/desarrollo amplio.")\n''',
    '''        appendLine("Adapta la extensión a la dificultad de la pregunta y al nivel de detalle elegido por el estudiante; no recortes una explicación académica útil solo por ser larga.")\n        appendLine("Responde siempre a la pregunta actual; no repitas una respuesta anterior si ya no corresponde al tema preguntado.")\n'''
)
replace_once(
    p,
    '''        appendLine("En teología católica distingue enseñanza oficial, disciplina, opinión teológica e interpretación académica.")\n''',
    '''        appendLine("En teología católica distingue enseñanza oficial, disciplina, opinión teológica e interpretación académica.")\n        if (pedagogicalMode) {\n            appendLine("Actúa como pedagogo académico: ayuda a comprender, planificar, priorizar, practicar recuperación activa y elegir métodos de estudio concretos.")\n            appendLine("No actúes como psicólogo ni hagas diagnósticos clínicos; mantente en el terreno del aprendizaje y la organización académica.")\n        }\n'''
)
replace_once(
    p,
    '        private const val MAX_FOLLOW_UP_CHARS = 2_400\n',
    '        private const val MAX_FOLLOW_UP_CHARS = 2_400\n        private const val MAX_WEB_CONTEXT_CHARS = 6_000\n        private const val MAX_VOCAB_CONTEXT_CHARS = 2_200\n'
)
replace_once(p, '        private const val GPU_FIRST_TOKEN_TIMEOUT_MS = 20_000L\n        private const val GENERATION_TIMEOUT_MS = 75_000L\n', '        private const val GPU_FIRST_TOKEN_TIMEOUT_MS = 30_000L\n')

# 5) UI: safe artifact streaming, fourth Assistance tab, study vocabulary action.
p = "app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt"
replace_once(
    p,
    '''    LaunchedEffect(result, artifactScope) {\n        if (autoSaveNextArtifact && result.isNotBlank()) {\n''',
    '''    LaunchedEffect(result, busy, artifactScope) {\n        if (autoSaveNextArtifact && !busy && result.isNotBlank()) {\n'''
)
old_when = '''                else -> AiStudio(\n                    subjectName = subjectName,\n                    classTitle = classTitle,\n                    configured = onlineConfigured,\n                    busy = busy,\n                    artifactRevision = artifactRevision,\n                    onAsk = { prompt, expectsArtifact ->\n                        autoSaveNextArtifact = expectsArtifact\n                        onAsk(prompt)\n                        section = 1\n                    },\n                    onOpenMap = { openedMap = it },\n                    onOpenDeck = { openedDeck = it },\n                    onOpenQuiz = { openedQuiz = it },\n                    onDeleteArtifact = { id ->\n                        artifactStore.delete(artifactScope, id)\n                        artifactRevision += 1\n                    }\n                )\n'''
new_when = '''                2 -> AiStudio(\n                    subjectName = subjectName,\n                    classTitle = classTitle,\n                    configured = onlineConfigured,\n                    busy = busy,\n                    artifactRevision = artifactRevision,\n                    onAsk = { prompt, expectsArtifact ->\n                        autoSaveNextArtifact = expectsArtifact\n                        onAsk(prompt)\n                        section = 1\n                    },\n                    onOpenMap = { openedMap = it },\n                    onOpenDeck = { openedDeck = it },\n                    onOpenQuiz = { openedQuiz = it },\n                    onDeleteArtifact = { id ->\n                        artifactStore.delete(artifactScope, id)\n                        artifactRevision += 1\n                    }\n                )\n                else -> AiAssistance(\n                    subjectName = subjectName,\n                    classTitle = classTitle,\n                    busy = busy,\n                    onAsk = { prompt ->\n                        onAsk(prompt)\n                        section = 1\n                    }\n                )\n'''
replace_once(p, old_when, new_when)
replace_once(
    p,
    '''            NavigationBarItem(selected = section == 2, onClick = { section = 2 }, icon = { Icon(Icons.Default.AutoAwesome, null) }, label = { Text("Estudio") })\n''',
    '''            NavigationBarItem(selected = section == 2, onClick = { section = 2 }, icon = { Icon(Icons.Default.AutoAwesome, null) }, label = { Text("Estudio") })\n            NavigationBarItem(selected = section == 3, onClick = { section = 3 }, icon = { Icon(Icons.Default.MenuBook, null) }, label = { Text("Asistencia") })\n'''
)
replace_once(
    p,
    '''        StudyTool("Resumen de clase", "Ideas principales, conceptos y estructura", Icons.Default.GraphicEq, "Haz un resumen estructurado de esta clase. Separa ideas principales, conceptos clave, definiciones y relaciones."),\n''',
    '''        StudyTool("Resumen de clase", "Ideas principales, conceptos y estructura", Icons.Default.GraphicEq, "Haz un resumen estructurado de esta clase. Separa ideas principales, conceptos clave, definiciones y relaciones."),\n        StudyTool("Vocabulario clave", "Términos académicos del ciclo y de esta materia", Icons.Default.MenuBook, "Usa también el vocabulario académico de NotCan. Identifica los términos más importantes para estudiar esta clase, respeta sus grafías y explica brevemente los que sean pertinentes."),\n'''
)

assistance = r'''

private data class AssistanceTool(
    val title: String,
    val subtitle: String,
    val prompt: String
)

@Composable
private fun AiAssistance(
    subjectName: String?,
    classTitle: String?,
    busy: Boolean,
    onAsk: (String) -> Unit
) {
    val options = listOf(
        AssistanceTool(
            "Organizar mi estudio",
            "Convierte una carga grande en un plan realista",
            "Ayúdame a organizar una sesión de estudio para esta materia. Prioriza lo esencial, divide el trabajo en bloques y pregúntame solo el tiempo disponible si hace falta."
        ),
        AssistanceTool(
            "Preparar un examen",
            "Orden, práctica activa y repaso",
            "Actúa como pedagogo y ayúdame a preparar un examen de esta materia. Propón una estrategia por etapas usando recuperación activa, práctica y repaso; adapta el plan al material disponible."
        ),
        AssistanceTool(
            "Elegir método de estudio",
            "Qué técnica conviene para este contenido",
            "Analiza el tipo de contenido de esta materia y recomiéndame métodos de estudio concretos. Explica cuándo usar preguntas activas, Feynman, repetición espaciada, mapas o simulacro oral."
        ),
        AssistanceTool(
            "Estoy atrasado",
            "Prioriza sin intentar hacerlo todo a la vez",
            "Estoy atrasado con el estudio. Ayúdame a priorizar académicamente: separa imprescindible, importante y aplazable, y propón el siguiente bloque de trabajo. Si necesitas un dato, pregunta solo lo imprescindible."
        ),
        AssistanceTool(
            "Cómo estudiar esta materia",
            "Estrategia adaptada al contenido",
            "Explícame cómo estudiar mejor esta materia según el material disponible: qué comprender, qué memorizar, qué practicar y cómo comprobar si realmente lo aprendí."
        ),
        AssistanceTool(
            "Plan de repaso",
            "Repasar sin releer todo desde cero",
            "Diseña un plan de repaso eficiente para esta materia con recuperación activa, intervalos de repaso y comprobaciones breves de dominio."
        )
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Asistencia pedagógica", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(subjectName, classTitle).joinToString(" · ").ifBlank { "Organización y métodos de estudio" },
                color = NotCanGray
            )
            Text("TuNot te ayuda a aprender, organizarte y elegir técnicas de estudio. No sustituye a un profesional de salud mental.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
        }
        items(options) { tool ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {
                    onAsk(
                        buildString {
                            appendLine(NotCanAiService.PEDAGOGY_MARKER)
                            appendLine(NotCanAiService.AUTO_WEB_MARKER)
                            append(tool.prompt)
                        }
                    )
                },
                colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = NotCanBlue.copy(alpha = 0.12f)) {
                        Icon(Icons.Default.MenuBook, null, tint = NotCanBlue, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tool.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                        Text(tool.subtitle, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
'''
text = read(p)
if 'private fun AiAssistance(' not in text:
    write(p, text.rstrip() + assistance + '\n')

print("TuNot Gemma study/assistance/web/vocabulary upgrade applied")
