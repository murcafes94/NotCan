from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str):
    text = path.read_text()
    if old not in text:
        raise SystemExit(f"{label}: pattern not found in {path}")
    path.write_text(text.replace(old, new, 1))

# 1) Extend the existing class-scoped source store with WEB sources.
path = Path('app/src/main/java/com/notcan/app/sources/ClassSourceStore.kt')
replace_once(path,
'''        val indexed: Boolean,\n        val enabled: Boolean = true,\n        val indexChars: Int = 0\n''',
'''        val indexed: Boolean,\n        val enabled: Boolean = true,\n        val indexChars: Int = 0,\n        val sourceUrl: String? = null\n''', 'sourceUrl field')

replace_once(path,
'''    fun reindex(item: SourceItem): SourceItem {\n        val file = File(item.localPath)\n        val index = SourceTextIndexer.index(context, file, item.type)\n''',
'''    fun importWeb(scopeKey: String, title: String, url: String, content: String): SourceItem {\n        require(url.startsWith("https://") || url.startsWith("http://")) { "URL web no válida" }\n        val cleanTitle = title.trim().ifBlank { url }.take(180)\n        val cleanText = content\n            .replace("\\u0000", "")\n            .replace(Regex("[\\t ]+"), " ")\n            .replace(Regex("\\n{3,}"), "\\n\\n")\n            .trim()\n            .take(180_000)\n        require(cleanText.isNotBlank()) { "La página no contiene texto legible" }\n\n        val id = UUID.randomUUID().toString()\n        val dir = scopeDir(scopeKey).apply { mkdirs() }\n        val file = File(dir, "${id.take(8)}_${sanitize(cleanTitle)}.web.txt")\n        val indexedText = buildString {\n            appendLine("TÍTULO: $cleanTitle")\n            appendLine("URL: $url")\n            appendLine("FECHA DE CONSULTA: ${java.time.Instant.now()}")\n            appendLine()\n            append(cleanText)\n        }\n        file.writeText(indexedText, Charsets.UTF_8)\n        SourceTextIndexer.indexFileFor(file).writeText(indexedText, Charsets.UTF_8)\n        val item = SourceItem(\n            id = id,\n            scopeKey = scopeKey,\n            displayName = cleanTitle,\n            type = "WEB",\n            mimeType = "text/plain",\n            localPath = file.absolutePath,\n            createdAtEpochMs = System.currentTimeMillis(),\n            indexed = true,\n            enabled = true,\n            indexChars = indexedText.length,\n            sourceUrl = url\n        )\n        saveItem(item)\n        return item\n    }\n\n    fun reindex(item: SourceItem): SourceItem {\n        val file = File(item.localPath)\n        val index = if (item.type == "WEB" && file.exists()) {\n            SourceTextIndexer.indexFileFor(file).also { it.writeText(file.readText(Charsets.UTF_8), Charsets.UTF_8) }\n        } else SourceTextIndexer.index(context, file, item.type)\n''', 'importWeb/reindex')

replace_once(path,
'''            out.appendLine("\\n=== FUENTE EXTERNA: ${item.displayName} (${item.type}) ===")\n            out.appendLine(text)\n''',
'''            out.appendLine("\\n=== FUENTE EXTERNA: ${item.displayName} (${item.type}) ===")\n            item.sourceUrl?.takeIf { it.isNotBlank() }?.let { out.appendLine("URL: $it") }\n            out.appendLine(text)\n''', 'web url in context')

replace_once(path,
'''        .put("enabled", enabled)\n        .put("indexChars", indexChars)\n''',
'''        .put("enabled", enabled)\n        .put("indexChars", indexChars)\n        .put("sourceUrl", sourceUrl)\n''', 'sourceUrl json write')

replace_once(path,
'''            enabled = optBoolean("enabled", true),\n            indexChars = optInt("indexChars", 0)\n''',
'''            enabled = optBoolean("enabled", true),\n            indexChars = optInt("indexChars", 0),\n            sourceUrl = optString("sourceUrl").takeIf { it.isNotBlank() && it != "null" }\n''', 'sourceUrl json read')

# 2) Add web search/reader to Sources.
path = Path('app/src/main/java/com/notcan/app/ui/ai/AiExternalSourcesPanel.kt')
replace_once(path,
'''/** External PDF/DOCX/EPUB library for TuNot. Files are indexed but never opened as a reader here. */''',
'''/** External PDF/DOCX/EPUB/WEB library for TuNot. All saved sources are indexed per class. */''', 'panel doc')
replace_once(path,
'''                Text("PDF, DOCX y EPUB · se indexan para buscar y para TuNot", color = NotCanGray, style = MaterialTheme.typography.bodySmall)''',
'''                Text("PDF, DOCX, EPUB y web · se indexan para buscar y para TuNot", color = NotCanGray, style = MaterialTheme.typography.bodySmall)''', 'panel subtitle')
replace_once(path,
'''        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }\n\n        if (sources.isEmpty()) {''',
'''        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }\n\n        TuNotWebSourcesPanel(\n            store = store,\n            scopeKey = scopeKey,\n            onSourcesChanged = ::refresh,\n            modifier = Modifier.fillMaxWidth()\n        )\n\n        if (sources.isEmpty()) {''', 'web panel insert')
replace_once(path,
'''                    Text("Todavía no hay archivos externos", color = NotCanOffWhite, fontWeight = FontWeight.Medium)\n                    Text("Añade bibliografía o material de clase. NotCan extraerá el texto localmente, pero no lo mostrará como lector ni lo convertirá en apunte.", color = NotCanGray)''',
'''                    Text("Todavía no hay fuentes guardadas", color = NotCanOffWhite, fontWeight = FontWeight.Medium)\n                    Text("Añade bibliografía o guarda una página web. NotCan indexará el contenido para que TuNot pueda usarlo en esta clase.", color = NotCanGray)''', 'panel empty text')

# 3) Make TuNot modes: My sources / Auto / Web and inject actual web research into Mistral.
path = Path('app/src/main/java/com/notcan/app/ai/NotCanAiService.kt')
replace_once(path,
'''    private val preferences = NotCanPreferences(appContext)\n    private val credentials = MistralCredentialsStore(appContext)\n''',
'''    private val preferences = NotCanPreferences(appContext)\n    private val credentials = MistralCredentialsStore(appContext)\n    private val webResearch = WebResearchService(appContext)\n''', 'web research field')
replace_once(path,
'''        val strictSources = question.contains(SOURCE_ONLY_MARKER)\n        val socraticMode = question.contains(SOCRATIC_MARKER)\n''',
'''        val strictSources = question.contains(SOURCE_ONLY_MARKER)\n        val forcedWeb = question.contains(WEB_SEARCH_MARKER)\n        val autoWeb = question.contains(AUTO_WEB_MARKER)\n        val socraticMode = question.contains(SOCRATIC_MARKER)\n''', 'mode flags')
replace_once(path,
'''            .replace(SOURCE_ONLY_MARKER, "")\n            .replace(SOCRATIC_MARKER, "")\n''',
'''            .replace(SOURCE_ONLY_MARKER, "")\n            .replace(WEB_SEARCH_MARKER, "")\n            .replace(AUTO_WEB_MARKER, "")\n            .replace(SOCRATIC_MARKER, "")\n''', 'clean markers')
replace_once(path,
'''        val plainNotes = sourcePlainText(notes)\n        val plainTranscript = sourcePlainText(transcript)\n\n        if (strictSources && plainNotes.isBlank() && plainTranscript.isBlank()) {''',
'''        val plainNotes = sourcePlainText(notes)\n        val plainTranscript = sourcePlainText(transcript)\n        val wantsWeb = !strictSources && (forcedWeb || (autoWeb && WebResearchService.shouldAutoSearch(cleanQuestion)))\n        val webResults = if (wantsWeb) {\n            runCatching { webResearch.research(cleanQuestion, limit = 5, readTop = 3) }.getOrDefault(emptyList())\n        } else emptyList()\n        val webContext = webResearch.formatForPrompt(webResults)\n\n        if (strictSources && plainNotes.isBlank() && plainTranscript.isBlank()) {''', 'perform web research')
replace_once(path,
'''            appendLine(TuNotCatholicSourcePolicy.promptPolicy())\n\n            if (strictSources) {''',
'''            appendLine(TuNotCatholicSourcePolicy.promptPolicy())\n            if (wantsWeb) {\n                appendLine("MODO WEB DE NOTCAN ACTIVADO.")\n                appendLine("NotCan realizó la búsqueda fuera de Mistral y te entrega FUENTES WEB reales debajo.")\n                appendLine("Usa esas fuentes para datos actuales o externos. No inventes URLs ni atribuciones.")\n                appendLine("Distingue claramente información recuperada de la web de conocimiento general o material de clase.")\n                appendLine("Al final incluye una sección breve 'Fuentes web' con título y URL de las fuentes que realmente hayas usado.")\n                if (webResults.isEmpty()) appendLine("La búsqueda no devolvió resultados utilizables; dilo explícitamente si la respuesta depende de información actual.")\n            }\n\n            if (strictSources) {''', 'web prompt policy')
replace_once(path,
'''            if (sourceText.isNotBlank()) {\n                appendLine("\\n--- MATERIAL DE CLASE DISPONIBLE ---")\n                appendLine(sourceText)\n                appendLine("--- FIN DEL MATERIAL DE CLASE ---\\n")\n            }\n\n            appendLine("SOLICITUD DEL USUARIO:")''',
'''            if (sourceText.isNotBlank()) {\n                appendLine("\\n--- MATERIAL DE CLASE DISPONIBLE ---")\n                appendLine(sourceText)\n                appendLine("--- FIN DEL MATERIAL DE CLASE ---\\n")\n            }\n            if (webContext.isNotBlank()) {\n                appendLine("\\n--- FUENTES WEB RECUPERADAS POR NOTCAN ---")\n                appendLine(webContext)\n                appendLine("--- FIN DE FUENTES WEB ---\\n")\n            }\n\n            appendLine("SOLICITUD DEL USUARIO:")''', 'web context insert')
replace_once(path,
'''        const val SOURCE_ONLY_MARKER = "[SOLO_FUENTES]"\n        const val SOCRATIC_MARKER = "[MODO_SOCRATICO]"\n''',
'''        const val SOURCE_ONLY_MARKER = "[SOLO_FUENTES]"\n        const val WEB_SEARCH_MARKER = "[BUSCAR_WEB_NOTCAN]"\n        const val AUTO_WEB_MARKER = "[AUTO_WEB_NOTCAN]"\n        const val SOCRATIC_MARKER = "[MODO_SOCRATICO]"\n''', 'web constants')

# 4) Chat selector.
path = Path('app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt')
replace_once(path,
'''    var question by remember(scopeKey) { mutableStateOf("") }\n    var sourceOnly by remember(scopeKey) { mutableStateOf(false) }\n    var socraticMode by remember(scopeKey) { mutableStateOf(false) }\n''',
'''    var question by remember(scopeKey) { mutableStateOf("") }\n    var sourceMode by remember(scopeKey) { mutableIntStateOf(1) } // 0 Mis fuentes · 1 Auto · 2 Web\n    var socraticMode by remember(scopeKey) { mutableStateOf(false) }\n''', 'chat source mode state')
replace_once(path,
'''        val prompt = buildString {\n            if (sourceOnly) appendLine(NotCanAiService.SOURCE_ONLY_MARKER)\n            if (socraticMode) {''',
'''        val prompt = buildString {\n            when (sourceMode) {\n                0 -> appendLine(NotCanAiService.SOURCE_ONLY_MARKER)\n                2 -> appendLine(NotCanAiService.WEB_SEARCH_MARKER)\n                else -> appendLine(NotCanAiService.AUTO_WEB_MARKER)\n            }\n            if (socraticMode) {''', 'submit source mode')
replace_once(path,
'''                        CompactAiTools(sourceOnly, socraticMode, messages.isNotEmpty(), { sourceOnly = it }, { socraticMode = it }, ::clearConversation, Modifier.width(250.dp))''',
'''                        CompactAiTools(sourceMode, socraticMode, messages.isNotEmpty(), { sourceMode = it }, { socraticMode = it }, ::clearConversation, Modifier.width(250.dp))''', 'wide tools call')
replace_once(path,
'''                    CompactAiTools(sourceOnly, socraticMode, messages.isNotEmpty(), { sourceOnly = it }, { socraticMode = it }, ::clearConversation, Modifier.fillMaxWidth())''',
'''                    CompactAiTools(sourceMode, socraticMode, messages.isNotEmpty(), { sourceMode = it }, { socraticMode = it }, ::clearConversation, Modifier.fillMaxWidth())''', 'mobile tools call')
replace_once(path,
'''private fun CompactAiTools(\n    sourceOnly: Boolean,\n    socraticMode: Boolean,\n    hasMessages: Boolean,\n    onSourceOnlyChange: (Boolean) -> Unit,\n    onSocraticChange: (Boolean) -> Unit,\n    onClear: () -> Unit,\n    modifier: Modifier = Modifier\n) {\n    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f)), shape = RoundedCornerShape(16.dp)) {\n        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n            FilterChip(selected = sourceOnly, onClick = { onSourceOnlyChange(!sourceOnly) }, label = { Text("Solo mis fuentes") }, leadingIcon = { Icon(Icons.Default.Source, null) })\n            FilterChip(selected = socraticMode, onClick = { onSocraticChange(!socraticMode) }, label = { Text("Socrático") }, leadingIcon = { Icon(Icons.Default.Quiz, null) })\n            if (hasMessages) TextButton(onClick = onClear) { Text("Nueva conversación") }\n        }\n    }\n}\n''',
'''private fun CompactAiTools(\n    sourceMode: Int,\n    socraticMode: Boolean,\n    hasMessages: Boolean,\n    onSourceModeChange: (Int) -> Unit,\n    onSocraticChange: (Boolean) -> Unit,\n    onClear: () -> Unit,\n    modifier: Modifier = Modifier\n) {\n    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f)), shape = RoundedCornerShape(16.dp)) {\n        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {\n            Text("Fuentes de respuesta", color = NotCanGray, style = MaterialTheme.typography.labelMedium)\n            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {\n                FilterChip(selected = sourceMode == 0, onClick = { onSourceModeChange(0) }, label = { Text("Mis fuentes") })\n                FilterChip(selected = sourceMode == 1, onClick = { onSourceModeChange(1) }, label = { Text("Auto") })\n                FilterChip(selected = sourceMode == 2, onClick = { onSourceModeChange(2) }, label = { Text("Web") })\n            }\n            Text(\n                when (sourceMode) {\n                    0 -> "Solo material guardado en NotCan"\n                    2 -> "Busca en DuckDuckGo antes de responder"\n                    else -> "Busca solo cuando la pregunta necesita información externa o actual"\n                },\n                color = NotCanGray,\n                style = MaterialTheme.typography.bodySmall\n            )\n            FilterChip(selected = socraticMode, onClick = { onSocraticChange(!socraticMode) }, label = { Text("Socrático") }, leadingIcon = { Icon(Icons.Default.Quiz, null) })\n            if (hasMessages) TextButton(onClick = onClear) { Text("Nueva conversación") }\n        }\n    }\n}\n''', 'compact tools replacement')

# 5) Version bump.
path = Path('app/build.gradle.kts')
replace_once(path, '        versionCode = 24\n        versionName = "0.8.7"\n', '        versionCode = 25\n        versionName = "0.8.8"\n', 'version bump')

print('NotCan 0.8.8 web research patches applied')
