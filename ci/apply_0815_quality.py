from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, text: str) -> None:
    (ROOT / path).write_text(text, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:160]!r}")
    write(path, text.replace(old, new, 1))


def replace_all(path: str, old: str, new: str, minimum: int = 1) -> None:
    text = read(path)
    count = text.count(old)
    if count < minimum:
        raise RuntimeError(f"Expected at least {minimum} occurrences in {path}, got {count}: {old[:120]!r}")
    write(path, text.replace(old, new))


def regex_once(path: str, pattern: str, replacement: str) -> None:
    text = read(path)
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise RuntimeError(f"Regex expected once in {path}, got {count}: {pattern[:120]!r}")
    write(path, updated)


# -----------------------------------------------------------------------------
# Version
# -----------------------------------------------------------------------------
p = "app/build.gradle.kts"
replace_once(p, '        versionCode = 33\n        versionName = "0.8.14.2"\n', '        versionCode = 34\n        versionName = "0.8.15"\n')


# -----------------------------------------------------------------------------
# Navigation: Materias is a real root destination, never an implicit first class.
# -----------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt"
replace_once(
    p,
    '''        viewModelScope.launch {\n            subjects.collect { list ->\n                if (_selectedSubjectId.value == null || list.none { it.id == _selectedSubjectId.value }) _selectedSubjectId.value = list.firstOrNull()?.id\n            }\n        }\n        viewModelScope.launch {\n            classes.collect { list ->\n                if (_selectedClassId.value == null || list.none { it.id == _selectedClassId.value }) _selectedClassId.value = list.firstOrNull()?.id\n            }\n        }\n''',
    '''        viewModelScope.launch {\n            subjects.collect { list ->\n                val selected = _selectedSubjectId.value\n                if (selected != null && list.none { it.id == selected }) {\n                    _selectedSubjectId.value = null\n                    _selectedClassId.value = null\n                    _selectedNoteId.value = null\n                }\n            }\n        }\n        viewModelScope.launch {\n            classes.collect { list ->\n                val selected = _selectedClassId.value\n                if (selected != null && list.none { it.id == selected }) {\n                    _selectedClassId.value = null\n                    _selectedNoteId.value = null\n                }\n            }\n        }\n'''
)
replace_once(
    p,
    '''    fun selectSubject(id: String) { _selectedSubjectId.value = id; _selectedClassId.value = null; _selectedNoteId.value = null }\n    fun selectClass(id: String) { _selectedClassId.value = id; _selectedNoteId.value = null }\n    fun selectNote(id: String) { _selectedNoteId.value = id }\n''',
    '''    fun openSubjects() {\n        _selectedSubjectId.value = null\n        _selectedClassId.value = null\n        _selectedNoteId.value = null\n    }\n\n    fun selectSubject(id: String) { _selectedSubjectId.value = id; _selectedClassId.value = null; _selectedNoteId.value = null }\n    fun selectClass(id: String) { _selectedClassId.value = id; _selectedNoteId.value = null }\n    fun selectNote(id: String) { _selectedNoteId.value = id }\n'''
)
replace_once(
    p,
    '''    fun createSubject(name: String) {\n        val parent = _selectedCycleId.value ?: return\n        if (name.isBlank()) return\n        viewModelScope.launch {\n            val item = repository.createSubject(parent, name)\n            _selectedSubjectId.value = item.id\n            _selectedClassId.value = null\n        }\n    }\n''',
    '''    fun createSubject(name: String) {\n        val parent = _selectedCycleId.value ?: return\n        if (name.isBlank()) return\n        viewModelScope.launch { repository.createSubject(parent, name) }\n    }\n'''
)
replace_once(
    p,
    '''    fun createClass(title: String) {\n        val parent = _selectedSubjectId.value ?: return\n        viewModelScope.launch {\n            val number = classes.value.size + 1\n            val resolvedTitle = title.trim().ifBlank { "Clase $number" }\n            _selectedClassId.value = repository.createClassSession(parent, resolvedTitle).id\n        }\n    }\n''',
    '''    fun createClass(title: String) {\n        val parent = _selectedSubjectId.value ?: return\n        viewModelScope.launch {\n            val number = classes.value.size + 1\n            val resolvedTitle = title.trim().ifBlank { "Clase $number" }\n            repository.createClassSession(parent, resolvedTitle)\n        }\n    }\n'''
)

p = "app/src/main/java/com/notcan/app/ui/home/NotCanHomeScreen.kt"
replace_once(
    p,
    '''    selectedClassId: String?,\n    selectedNoteId: String?,\n    classNavigationRequest: Int = 0,\n''',
    '''    selectedClassId: String?,\n    selectedNoteId: String?,\n    subjectNavigationRequest: Int = 0,\n    classNavigationRequest: Int = 0,\n'''
)
replace_once(
    p,
    '''    LaunchedEffect(classNavigationRequest, selectedSubjectId) {\n        if (classNavigationRequest > 0 && selectedSubjectId != null) level = HomeLevel.CLASSES\n    }\n''',
    '''    LaunchedEffect(subjectNavigationRequest) {\n        if (subjectNavigationRequest > 0) level = HomeLevel.SUBJECTS\n    }\n\n    LaunchedEffect(classNavigationRequest, selectedSubjectId) {\n        if (classNavigationRequest > 0 && selectedSubjectId != null) level = HomeLevel.CLASSES\n    }\n'''
)

p = "app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt"
replace_once(
    p,
    '''    onToggleDoNotDisturb: () -> Unit = {},\n    onOpenClasses: () -> Unit = {},\n''',
    '''    onToggleDoNotDisturb: () -> Unit = {},\n    onOpenSubjects: () -> Unit = {},\n    onOpenClasses: () -> Unit = {},\n'''
)
replace_once(
    p,
    '                                    onClick = { page = d.page; navExpanded = false },\n',
    '                                    onClick = { page = d.page; if (d.page == 1) onOpenSubjects(); navExpanded = false },\n'
)
replace_once(
    p,
    '                            onClick = { page = d.page },\n',
    '                            onClick = { page = d.page; if (d.page == 1) onOpenSubjects() },\n'
)
replace_all(
    p,
    '{ page = it }',
    '{ target -> page = target; if (target == 1) onOpenSubjects() }',
    minimum=2
)

p = "app/src/main/java/com/notcan/app/MainActivity.kt"
replace_once(
    p,
    '''            var darkTheme by remember { mutableStateOf(preferences.darkTheme) }\n            var classNavigationRequest by remember { mutableIntStateOf(0) }\n''',
    '''            var darkTheme by remember { mutableStateOf(preferences.darkTheme) }\n            var subjectNavigationRequest by remember { mutableIntStateOf(0) }\n            var classNavigationRequest by remember { mutableIntStateOf(0) }\n'''
)
replace_once(
    p,
    '''                    onToggleDoNotDisturb = ::toggleDoNotDisturb,\n                    onOpenClasses = { classNavigationRequest++ },\n''',
    '''                    onToggleDoNotDisturb = ::toggleDoNotDisturb,\n                    onOpenSubjects = { studyViewModel.openSubjects(); subjectNavigationRequest++ },\n                    onOpenClasses = { classNavigationRequest++ },\n'''
)
replace_once(
    p,
    '''                            selectedClassId = selectedClassId,\n                            selectedNoteId = selectedNoteId,\n                            classNavigationRequest = classNavigationRequest,\n''',
    '''                            selectedClassId = selectedClassId,\n                            selectedNoteId = selectedNoteId,\n                            subjectNavigationRequest = subjectNavigationRequest,\n                            classNavigationRequest = classNavigationRequest,\n'''
)


# -----------------------------------------------------------------------------
# Writer editor: isolate each note, save pending edits before disposal, recover drafts,
# and never let an empty stale WebView overwrite a non-empty note.
# -----------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/home/WriterNoteEditor.kt"
replace_once(p, 'import androidx.compose.runtime.getValue\n', 'import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.key\n')
replace_once(
    p,
    '''    val title = note.title.ifBlank { "Apuntes" }\n    var html by remember(note.id) { mutableStateOf(normalizeStoredBody(note.body)) }\n    var lastSavedHtml by remember(note.id) { mutableStateOf(normalizeStoredBody(note.body)) }\n    var webView by remember(note.id) { mutableStateOf<WebView?>(null) }\n    var confirmDelete by remember(note.id) { mutableStateOf(false) }\n    var shareMenu by remember(note.id) { mutableStateOf(false) }\n    var editing by remember(note.id) { mutableStateOf(false) }\n''',
    '''    val title = note.title.ifBlank { "Apuntes" }\n    val draftPreferences = remember(context) {\n        context.applicationContext.getSharedPreferences("notcan_note_drafts", Context.MODE_PRIVATE)\n    }\n    val draftKey = "body_${note.id}"\n    val draftTimeKey = "time_${note.id}"\n    val initialHtml = remember(note.id) {\n        val stored = normalizeStoredBody(note.body)\n        val draft = draftPreferences.getString(draftKey, null)\n        val draftTime = draftPreferences.getLong(draftTimeKey, 0L)\n        if (!draft.isNullOrBlank() && draftTime > note.updatedAtEpochMs && !isEffectivelyEmptyHtml(draft)) draft else stored\n    }\n    var html by remember(note.id) { mutableStateOf(initialHtml) }\n    var lastSavedHtml by remember(note.id) { mutableStateOf(normalizeStoredBody(note.body)) }\n    var webView by remember(note.id) { mutableStateOf<WebView?>(null) }\n    var bridge by remember(note.id) { mutableStateOf<NoteBridge?>(null) }\n    var userEdited by remember(note.id) { mutableStateOf(false) }\n    var confirmDelete by remember(note.id) { mutableStateOf(false) }\n    var shareMenu by remember(note.id) { mutableStateOf(false) }\n    var editing by remember(note.id) { mutableStateOf(false) }\n'''
)
replace_once(
    p,
    '''    LaunchedEffect(note.id, note.body) {\n        val externalHtml = normalizeStoredBody(note.body)\n        if (externalHtml != html && externalHtml != lastSavedHtml) {\n            html = externalHtml\n            lastSavedHtml = externalHtml\n            webView?.loadDataWithBaseURL(null, writerDocument(externalHtml, darkEditor), "text/html", "UTF-8", null)\n        } else if (externalHtml == html) {\n            lastSavedHtml = externalHtml\n        }\n    }\n''',
    '''    LaunchedEffect(note.id, note.body) {\n        val externalHtml = normalizeStoredBody(note.body)\n        val localDirty = html != lastSavedHtml\n        when {\n            externalHtml == html -> lastSavedHtml = externalHtml\n            !localDirty -> {\n                html = externalHtml\n                lastSavedHtml = externalHtml\n                webView?.loadDataWithBaseURL(null, writerDocument(externalHtml, darkEditor), "text/html", "UTF-8", null)\n            }\n            externalHtml == lastSavedHtml -> Unit\n            else -> Unit // Preserve the newer local draft until it is saved.\n        }\n    }\n'''
)
replace_once(
    p,
    '''    LaunchedEffect(note.id, html) {\n        delay(500)\n        if (html != lastSavedHtml) {\n            onUpdateNote(note.id, title, html)\n            lastSavedHtml = html\n        }\n    }\n\n    DisposableEffect(note.id) {\n        onDispose {\n            webView?.removeJavascriptInterface("NotCanBridge")\n            webView?.destroy()\n            webView = null\n        }\n    }\n''',
    '''    LaunchedEffect(note.id, html) {\n        val pending = html\n        draftPreferences.edit().putString(draftKey, pending).putLong(draftTimeKey, System.currentTimeMillis()).apply()\n        delay(350)\n        val safeToPersist = userEdited || !isEffectivelyEmptyHtml(pending) || isEffectivelyEmptyHtml(lastSavedHtml)\n        if (pending != lastSavedHtml && safeToPersist) {\n            onUpdateNote(note.id, title, pending)\n            lastSavedHtml = pending\n        }\n    }\n\n    DisposableEffect(note.id) {\n        onDispose {\n            val pending = html\n            draftPreferences.edit().putString(draftKey, pending).putLong(draftTimeKey, System.currentTimeMillis()).apply()\n            val safeToPersist = userEdited || !isEffectivelyEmptyHtml(pending) || isEffectivelyEmptyHtml(lastSavedHtml)\n            if (pending != lastSavedHtml && safeToPersist) onUpdateNote(note.id, title, pending)\n            bridge?.deactivate()\n            webView?.removeJavascriptInterface("NotCanBridge")\n            webView?.destroy()\n            bridge = null\n            webView = null\n        }\n    }\n'''
)
regex_once(
    p,
    r'''            AndroidView\(\n                modifier = Modifier\.fillMaxWidth\(\)\.weight\(1f\),\n                factory = \{.*?\n                update = \{ view -> if \(webView !== view\) webView = view \}\n            \)\n            if \(!landscapeIme\)''',
    '''            key(note.id) {\n                AndroidView(\n                    modifier = Modifier.fillMaxWidth().weight(1f),\n                    factory = {\n                        NotCanWriterWebView(context).apply {\n                            setBackgroundColor(android.graphics.Color.TRANSPARENT)\n                            settings.javaScriptEnabled = true\n                            settings.domStorageEnabled = false\n                            settings.allowContentAccess = false\n                            settings.allowFileAccess = false\n                            settings.setSupportZoom(false)\n                            isVerticalScrollBarEnabled = true\n                            webViewClient = WebViewClient()\n                            val activeBridge = NoteBridge(note.id) { bridgeNoteId, newHtml ->\n                                if (bridgeNoteId == note.id) {\n                                    userEdited = true\n                                    html = newHtml\n                                    draftPreferences.edit()\n                                        .putString(draftKey, newHtml)\n                                        .putLong(draftTimeKey, System.currentTimeMillis())\n                                        .apply()\n                                }\n                            }\n                            bridge = activeBridge\n                            addJavascriptInterface(activeBridge, "NotCanBridge")\n                            loadDataWithBaseURL(null, writerDocument(html, darkEditor), "text/html", "UTF-8", null)\n                            webView = this\n                        }\n                    },\n                    update = { view -> if (webView !== view) webView = view }\n                )\n            }\n            if (!landscapeIme)'''
)
replace_once(
    p,
    '''private class NoteBridge(private val onChanged: (String) -> Unit) {\n    private val main = Handler(Looper.getMainLooper())\n    @JavascriptInterface fun onContentChanged(value: String) { main.post { onChanged(value) } }\n}\n\nprivate fun normalizeStoredBody(value: String): String {\n''',
    '''private class NoteBridge(\n    private val noteId: String,\n    private val onChanged: (String, String) -> Unit\n) {\n    private val main = Handler(Looper.getMainLooper())\n    @Volatile private var active = true\n\n    @JavascriptInterface\n    fun onContentChanged(value: String) {\n        main.post { if (active) onChanged(noteId, value) }\n    }\n\n    fun deactivate() { active = false }\n}\n\nprivate fun isEffectivelyEmptyHtml(value: String): Boolean = value\n    .replace(Regex("(?is)<br\\s*/?>"), "")\n    .replace(Regex("(?is)<[^>]+>"), "")\n    .replace("&nbsp;", "")\n    .replace("&#160;", "")\n    .trim()\n    .isEmpty()\n\nprivate fun normalizeStoredBody(value: String): String {\n'''
)


# -----------------------------------------------------------------------------
# Groq: strengthen literal behavior and recognition hints without semantic rewriting.
# -----------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/localai/AcademicTranscriptionSupport.kt"
replace_once(
    p,
    '''        "Padres apostólicos", "Patrística", "Cristología bíblica", "Cristología patrística",\n        "Nicea", "Constantinopla", "Éfeso", "Calcedonia", "Concilio de Nicea",\n''',
    '''        "Padres apostólicos", "Patrística", "Cristología bíblica", "Cristología patrística",\n        "Iglesia prenicena", "respuesta de la Iglesia", "para la salvación",\n        "Nicea", "Constantinopla", "Éfeso", "Calcedonia", "Concilio de Nicea",\n'''
)

p = "app/src/main/java/com/notcan/app/localai/GroqTranscriptionService.kt"
replace_once(
    p,
    '        parts += "Transcribe literalmente en español. Conserva palabras y frases inusuales tal como se oyen; no reformules."\n',
    '        parts += "Transcribe literalmente en español. No añadas introducciones ni palabras no pronunciadas. Conserva frases inusuales, números y años tal como se oyen; no reformules."\n'
)
replace_once(
    p,
    '''        subjectName?.trim()?.takeIf { it.isNotBlank() }?.let { parts += "Materia: ${it.take(90)}." }\n        classTitle?.trim()?.takeIf { it.isNotBlank() }?.let { parts += "Clase: ${it.take(90)}." }\n''',
    '''        subjectName?.trim()?.takeIf { it.isNotBlank() }?.let { parts += "Materia: ${it.take(90)}." }\n        classTitle?.trim()?.takeIf { it.isNotBlank() }?.let { parts += "Clase: ${it.take(90)}." }\n        parts += "Año académico de referencia: ${java.time.Year.now().value}."\n'''
)


# -----------------------------------------------------------------------------
# TuNot offline: full local routing, maps + flashcards + quizzes + extractive answers.
# -----------------------------------------------------------------------------
OFFLINE_ENGINE = r'''package com.notcan.app.ai

import com.notcan.app.ui.ai.TuNotOfflineEntry
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/**
 * Motor local determinista de TuNot. Trabaja únicamente con material guardado en NotCan:
 * no necesita Mistral, Internet ni un modelo generativo. Las respuestas son extractivas y
 * los artefactos se construyen a partir de frases realmente presentes en las fuentes.
 */
object OfflineTuNotEngine {
    fun isMapRequest(value: String): Boolean {
        val normalized = normalize(value)
        return listOf(
            "mapa mental", "mapa conceptual", "mapa de ideas", "haz un mapa",
            "hazme un mapa", "crea un mapa", "creame un mapa", "organiza en un mapa",
            "organizalo en un mapa", "ponlo en un mapa", "muestralo como mapa",
            "hazlo mas visual"
        ).any(normalized::contains)
    }

    fun isFlashcardRequest(value: String): Boolean {
        val normalized = normalize(value)
        return listOf("tarjetas", "flashcards", "flash cards", "fichas de estudio", "tarjetas didacticas").any(normalized::contains)
    }

    fun isQuizRequest(value: String): Boolean {
        val normalized = normalize(value)
        return listOf("cuestionario", "quiz", "preguntas de estudio", "preguntas para estudiar", "examen de practica").any(normalized::contains)
    }

    fun answer(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String
    ): String {
        val entries = buildList {
            if (notes.isNotBlank()) add(TuNotOfflineEntry("Apuntes", subjectName ?: "Material local", notes))
            if (transcript.isNotBlank()) add(TuNotOfflineEntry("Transcripción", subjectName ?: "Material local", transcript))
        }
        return answerEntries(subjectName ?: "Material de estudio", entries, question)
    }

    fun answerEntries(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        val usable = entries.filter { cleanText(it.text).isNotBlank() }
        if (usable.isEmpty()) {
            return "Modo local: todavía no hay apuntes, transcripciones o documentos indexados con contenido para trabajar sin conexión."
        }
        return when {
            isFlashcardRequest(question) -> buildFlashcardsArtifact(contextTitle, usable, question)
            isQuizRequest(question) -> buildQuizArtifact(contextTitle, usable, question)
            isMapRequest(question) -> buildMapArtifact(contextTitle, usable, question)
            else -> buildExtractiveAnswer(usable, question)
        }
    }

    private data class Candidate(
        val entry: TuNotOfflineEntry,
        val sentence: String,
        val score: Int
    )

    private fun buildMapArtifact(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        val normalizedQuestion = normalize(question)
        val type = if (normalizedQuestion.contains("conceptual")) "concept_map" else "mind_map"
        val layout = when {
            normalizedQuestion.contains("ideas") -> "ideas"
            normalizedQuestion.contains("radial") -> "radial_cards"
            type == "concept_map" -> "tree"
            else -> "horizontal"
        }
        val title = inferMapTitle(question, contextTitle)
        val tokens = queryTokens(question)
        val ranked = rankedSentences(entries, tokens)

        val selected = mutableListOf<Candidate>()
        val labels = mutableSetOf<String>()
        for (candidate in ranked) {
            val label = conceptLabel(candidate.sentence)
            val normalizedLabel = normalize(label)
            if (label.length !in 3..64 || normalizedLabel in labels) continue
            labels += normalizedLabel
            selected += candidate
            if (selected.size >= 10) break
        }
        if (selected.isEmpty()) selected += ranked.take(8)

        val nodes = JSONArray()
        val edges = JSONArray()
        nodes.put(
            JSONObject()
                .put("id", "root")
                .put("title", title)
                .put("description", "Mapa local construido únicamente con material guardado en NotCan")
                .put("level", 0)
                .put("source_refs", JSONArray(selected.map { it.entry.title }.distinct()))
        )

        selected.forEachIndexed { index, candidate ->
            val id = "n${index + 1}"
            val label = conceptLabel(candidate.sentence).ifBlank { "Concepto ${index + 1}" }
            nodes.put(
                JSONObject()
                    .put("id", id)
                    .put("title", compactTitle(label, 48))
                    .put("description", compactTitle(candidate.sentence, 180))
                    .put("level", 1)
                    .put("source_refs", JSONArray(listOf(candidate.entry.title)))
            )
            edges.put(
                JSONObject()
                    .put("from", "root")
                    .put("to", id)
                    .put("label", if (type == "concept_map") relationLabel(candidate.sentence) else "")
            )
        }

        val root = JSONObject()
            .put("type", type)
            .put("title", title)
            .put("layout", layout)
            .put("root_node_id", "root")
            .put("nodes", nodes)
            .put("edges", edges)
        return "<<<NOTCAN_MAP>>>\n${root}\n<<<END_NOTCAN_MAP>>>"
    }

    private fun buildFlashcardsArtifact(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        val tokens = queryTokens(question)
        val candidates = rankedSentences(entries, tokens).take(18)
        val cards = JSONArray()
        val usedQuestions = mutableSetOf<String>()
        for (candidate in candidates) {
            val qa = sentenceToQuestion(candidate.sentence)
            val normalizedQuestion = normalize(qa.first)
            if (normalizedQuestion in usedQuestions) continue
            usedQuestions += normalizedQuestion
            cards.put(
                JSONObject()
                    .put("question", compactTitle(qa.first, 210))
                    .put("answer", compactTitle(candidate.sentence, 500))
                    .put("source_ref", candidate.entry.title)
            )
            if (cards.length() >= 16) break
        }
        if (cards.length() == 0) return "Modo local: no encontré suficiente contenido legible para generar tarjetas."
        val root = JSONObject()
            .put("title", "Tarjetas · ${compactTitle(contextTitle, 58)}")
            .put("cards", cards)
        return "<<<NOTCAN_FLASHCARDS>>>\n${root}\n<<<END_NOTCAN_FLASHCARDS>>>"
    }

    private fun buildQuizArtifact(contextTitle: String, entries: List<TuNotOfflineEntry>, question: String): String {
        val tokens = queryTokens(question)
        val candidates = rankedSentences(entries, tokens).take(20)
        val questions = JSONArray()
        val used = mutableSetOf<String>()
        candidates.forEachIndexed { index, candidate ->
            val qa = sentenceToQuestion(candidate.sentence)
            val prompt = if (index % 3 == 0) {
                "Verdadero o falso: ${compactTitle(candidate.sentence, 250)}"
            } else qa.first
            if (!used.add(normalize(prompt))) return@forEachIndexed
            val type = if (index % 3 == 0) "true_false" else "short_answer"
            val correct = if (type == "true_false") "Verdadero" else compactTitle(candidate.sentence, 480)
            val item = JSONObject()
                .put("id", "q${questions.length() + 1}")
                .put("type", type)
                .put("question", compactTitle(prompt, 310))
                .put("options", JSONArray())
                .put("correct_answer", correct)
                .put("explanation", "Según ${candidate.entry.title}: ${compactTitle(candidate.sentence, 430)}")
                .put("source_ref", candidate.entry.title)
            questions.put(item)
        }
        if (questions.length() == 0) return "Modo local: no encontré suficiente contenido legible para generar un cuestionario."
        val root = JSONObject()
            .put("title", "Cuestionario · ${compactTitle(contextTitle, 55)}")
            .put("questions", questions)
        return "<<<NOTCAN_QUIZ>>>\n${root}\n<<<END_NOTCAN_QUIZ>>>"
    }

    private fun buildExtractiveAnswer(entries: List<TuNotOfflineEntry>, question: String): String {
        val tokens = queryTokens(question)
        val selected = rankedSentences(entries, tokens)
            .filter { it.score > 0 || tokens.isEmpty() }
            .distinctBy { normalize(it.sentence).take(120) }
            .take(6)
        if (selected.isEmpty()) {
            return "Modo local: no encontré esa información en el material guardado. Prueba con una palabra, concepto o título que aparezca en tus apuntes o transcripciones."
        }
        return buildString {
            appendLine("Modo local · basado únicamente en tu material guardado")
            selected.forEach { candidate ->
                append("• ")
                append(compactTitle(candidate.sentence, 310))
                append("  [${candidate.entry.title}]")
                appendLine()
            }
        }.trim()
    }

    private fun rankedSentences(entries: List<TuNotOfflineEntry>, tokens: List<String>): List<Candidate> = entries
        .flatMap { entry ->
            splitSentences(entry.text).map { sentence ->
                Candidate(entry, sentence, sentenceScore(sentence, tokens) + score(entry, tokens))
            }
        }
        .sortedWith(compareByDescending<Candidate> { it.score }.thenByDescending { informationScore(it.sentence) })
        .distinctBy { normalize(it.sentence).take(140) }

    private fun sentenceToQuestion(sentence: String): Pair<String, String> {
        val clean = sentence.trim().trimEnd('.', ';', ':')
        val pattern = Regex("^(.{2,80}?)\\s+(es|son|consiste en|se define como|implica|incluye|comprende)\\s+(.+)$", RegexOption.IGNORE_CASE)
        val match = pattern.find(clean)
        if (match != null) {
            val subject = compactTitle(match.groupValues[1].trim(), 74)
            val relation = normalize(match.groupValues[2])
            val question = when {
                relation == "son" -> "¿Qué son $subject?"
                relation.contains("implica") -> "¿Qué implica $subject?"
                relation.contains("incluye") || relation.contains("comprende") -> "¿Qué incluye $subject?"
                else -> "¿Qué es $subject?"
            }
            return question to clean
        }
        val label = conceptLabel(clean)
        return "¿Qué indica el material sobre $label?" to clean
    }

    private fun conceptLabel(sentence: String): String {
        val clean = compactTitle(cleanText(sentence), 180).trimEnd('.', ';', ':')
        val prefix = clean.substringBefore(':', "").trim()
        if (prefix.length in 3..46 && ':' in clean) return prefix
        val definition = Regex("^(.{3,58}?)\\s+(es|son|consiste en|se define como|implica|incluye|comprende)\\b", RegexOption.IGNORE_CASE).find(clean)
        if (definition != null) return definition.groupValues[1].trim()
        val words = clean.split(Regex("\\s+"))
            .map { it.trim(' ', ',', '.', ';', ':', '¿', '?', '¡', '!') }
            .filter { it.length >= 3 && normalize(it) !in stopWords }
            .take(5)
        return words.joinToString(" ").ifBlank { clean.take(46) }
    }

    private fun relationLabel(sentence: String): String {
        val n = normalize(sentence)
        return when {
            " implica " in " $n " -> "implica"
            " incluye " in " $n " || " comprende " in " $n " -> "incluye"
            Regex("\\b(es|son|se define)\\b").containsMatchIn(n) -> "define"
            " causa " in " $n " || " consecuencia " in " $n " -> "relaciona"
            else -> "explica"
        }
    }

    private fun score(entry: TuNotOfflineEntry, tokens: List<String>): Int {
        if (tokens.isEmpty()) return 1
        val title = normalize(entry.title)
        val subtitle = normalize(entry.subtitle)
        val text = normalize(entry.text)
        return tokens.fold(0) { total, token ->
            total + when {
                title.contains(token) -> 8
                subtitle.contains(token) -> 5
                text.contains(token) -> 2
                else -> 0
            }
        }
    }

    private fun sentenceScore(sentence: String, tokens: List<String>): Int {
        if (tokens.isEmpty()) return informationScore(sentence)
        val normalized = normalize(sentence)
        val hits = tokens.count(normalized::contains) * 5
        return hits + informationScore(sentence)
    }

    private fun informationScore(sentence: String): Int {
        var score = 0
        if (Regex("(?i)\\b(es|son|significa|consiste|se define|implica|incluye|comprende|causa|consecuencia)\\b").containsMatchIn(sentence)) score += 4
        if (sentence.length in 35..240) score += 2
        if (sentence.count { it == ',' } in 1..4) score += 1
        return score
    }

    private fun splitSentences(text: String): List<String> = cleanText(text)
        .split(Regex("(?<=[.!?;:])\\s+|[•\\n]+"))
        .map { it.trim(' ', '-', '•', '\\t') }
        .filter { it.length in 20..520 }

    private fun cleanText(text: String): String = text
        .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
        .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace(Regex("[ \\t\\r]+"), " ")
        .replace(Regex("\\n{3,}"), "\\n\\n")
        .trim()

    private fun inferMapTitle(question: String, fallback: String): String {
        val cleaned = question
            .replace(Regex("(?i)hazme|haz|hacer|genera|generar|crea|crear|mapa|mental|conceptual|de ideas|organiza|organizar|muestralo|muéstralo|esta clase|la clase|de la clase|sobre|con"), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ':', ',', '.', '?', '¿')
        val normalized = normalize(cleaned)
        val generic = cleaned.isBlank() || normalized in setOf("clase", "ideas", "estudio", "tema") || cleaned.length < 4
        return compactTitle(if (generic) fallback else cleaned, 64).ifBlank { "Mapa de estudio" }
    }

    private fun compactTitle(value: String, maxChars: Int): String {
        val cleaned = value.replace(Regex("\\s+"), " ").trim()
        if (cleaned.length <= maxChars) return cleaned
        val raw = cleaned.take(maxChars)
        val shortened = raw.substringBeforeLast(' ', raw)
        return shortened.trimEnd() + "…"
    }

    private fun queryTokens(value: String): List<String> = normalize(value)
        .split(Regex("\\s+"))
        .filter { it.length >= 3 && it !in stopWords }
        .distinct()

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private val stopWords = setOf(
        "para", "como", "una", "uno", "unos", "unas", "que", "con", "por", "del", "las", "los",
        "esta", "este", "esa", "ese", "sobre", "mapa", "mental", "conceptual", "hazme", "crea", "genera",
        "clase", "material", "fuente", "fuentes", "estudio", "segun", "desde", "entre", "tambien", "donde"
    )
}
'''
write("app/src/main/java/com/notcan/app/ai/OfflineTuNotEngine.kt", OFFLINE_ENGINE)

p = "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
replace_once(
    p,
    '''        require(isConfigured()) {\n            "Configura tu API key y Agent ID de Mistral en Configuración → Asistente NotCan."\n        }\n\n''',
    ''
)
replace_once(
    p,
    '''            .replace(QUIZ_MARKER, "")\n            .trim()\n        val mapRequest = OfflineTuNotEngine.isMapRequest(cleanQuestion) && !flashcardRequest && !quizRequest\n''',
    '''            .replace(QUIZ_MARKER, "")\n            .trim()\n        val localQuestion = buildString {\n            append(cleanQuestion)\n            if (flashcardRequest) append(" · tarjetas didácticas")\n            if (quizRequest) append(" · cuestionario")\n        }\n        val mapRequest = OfflineTuNotEngine.isMapRequest(cleanQuestion) && !flashcardRequest && !quizRequest\n'''
)
replace_once(
    p,
    '''        val plainNotes = sourcePlainText(notes)\n        val plainTranscript = sourcePlainText(transcript)\n        val wantsWeb = !strictSources && (forcedWeb || (autoWeb && WebResearchService.shouldAutoSearch(cleanQuestion)))\n        val webResults = if (wantsWeb) {\n            runCatching { webResearch.research(cleanQuestion, limit = 5, readTop = 3) }.getOrDefault(emptyList())\n        } else emptyList()\n        val webContext = webResearch.formatForPrompt(webResults)\n\n        if (strictSources && plainNotes.isBlank() && plainTranscript.isBlank()) {\n            return "No hay apuntes ni transcripciones disponibles para responder en modo Solo mis fuentes."\n        }\n''',
    '''        val plainNotes = sourcePlainText(notes)\n        val plainTranscript = sourcePlainText(transcript)\n        if (strictSources && plainNotes.isBlank() && plainTranscript.isBlank()) {\n            return "No hay apuntes ni transcripciones disponibles para responder en modo Solo mis fuentes."\n        }\n        if (!isConfigured()) {\n            return OfflineTuNotEngine.answer(\n                subjectName = subjectName,\n                notes = plainNotes,\n                transcript = plainTranscript,\n                question = localQuestion\n            )\n        }\n\n        val wantsWeb = !strictSources && (forcedWeb || (autoWeb && WebResearchService.shouldAutoSearch(cleanQuestion)))\n        val webResults = if (wantsWeb) {\n            runCatching { webResearch.research(cleanQuestion, limit = 5, readTop = 3) }.getOrDefault(emptyList())\n        } else emptyList()\n        val webContext = webResearch.formatForPrompt(webResults)\n'''
)
replace_once(
    p,
    '''                    question = cleanQuestion\n''',
    '''                    question = localQuestion\n'''
)


# -----------------------------------------------------------------------------
# TuNot UI: local mode is first-class, studio works offline, copy assistant text,
# and remove the duplicated inner TuNot heading.
# -----------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt"
replace_once(
    p,
    'package com.notcan.app.ui.ai\n\n',
    'package com.notcan.app.ui.ai\n\nimport android.content.ClipData\nimport android.content.ClipboardManager\nimport android.content.Context\nimport android.widget.Toast\n'
)
replace_once(p, 'import androidx.compose.material.icons.filled.Chat\n', 'import androidx.compose.material.icons.filled.Chat\nimport androidx.compose.material.icons.filled.ContentCopy\n')
replace_once(
    p,
    '        if (cleanQuestion.isBlank() || busy || !configured) return\n',
    '        if (cleanQuestion.isBlank() || busy) return\n'
)
replace_once(
    p,
    '''private fun CompactChatHeader(subjectName: String?, classTitle: String?, configured: Boolean, toolsOpen: Boolean, onToggleTools: () -> Unit) {\n    Row(verticalAlignment = Alignment.CenterVertically) {\n        Surface(shape = RoundedCornerShape(13.dp), color = NotCanBlue.copy(alpha = 0.13f)) {\n            Icon(Icons.Default.AutoAwesome, null, tint = NotCanBlue, modifier = Modifier.padding(9.dp))\n        }\n        Spacer(Modifier.width(10.dp))\n        Column(Modifier.weight(1f)) {\n            Text("TuNot", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)\n            Text(listOfNotNull(subjectName, classTitle).joinToString(" · ").ifBlank { "Asistente académico" }, color = NotCanGray, style = MaterialTheme.typography.bodySmall, maxLines = 1)\n        }\n        ConnectionBadge(configured)\n        IconButton(onClick = onToggleTools) { Icon(Icons.Default.Menu, if (toolsOpen) "Ocultar opciones" else "Opciones de TuNot", tint = NotCanBlue) }\n    }\n}\n''',
    '''private fun CompactChatHeader(subjectName: String?, classTitle: String?, configured: Boolean, toolsOpen: Boolean, onToggleTools: () -> Unit) {\n    Row(verticalAlignment = Alignment.CenterVertically) {\n        Column(Modifier.weight(1f)) {\n            Text(\n                listOfNotNull(subjectName, classTitle).joinToString(" · ").ifBlank { "Asistente académico" },\n                color = NotCanOffWhite,\n                style = MaterialTheme.typography.titleMedium,\n                fontWeight = FontWeight.SemiBold,\n                maxLines = 1\n            )\n        }\n        ConnectionBadge(configured)\n        IconButton(onClick = onToggleTools) { Icon(Icons.Default.Menu, if (toolsOpen) "Ocultar opciones" else "Opciones de TuNot", tint = NotCanBlue) }\n    }\n}\n'''
)
replace_once(
    p,
    '''private fun ConnectionBadge(configured: Boolean) {\n    Surface(color = if (configured) NotCanBlue.copy(alpha = 0.13f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {\n        Text(\n            if (configured) "Mistral" else "Sin configurar",\n            color = if (configured) NotCanBlue else MaterialTheme.colorScheme.error,\n            style = MaterialTheme.typography.labelMedium,\n            fontWeight = FontWeight.SemiBold,\n            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)\n        )\n    }\n}\n''',
    '''private fun ConnectionBadge(configured: Boolean) {\n    Surface(color = NotCanBlue.copy(alpha = if (configured) 0.13f else 0.09f), shape = RoundedCornerShape(50)) {\n        Text(\n            if (configured) "Mistral" else "Local",\n            color = if (configured) NotCanBlue else NotCanGray,\n            style = MaterialTheme.typography.labelMedium,\n            fontWeight = FontWeight.SemiBold,\n            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)\n        )\n    }\n}\n'''
)
replace_once(
    p,
    '                    placeholder = { Text(if (configured) "Pregunta a TuNot…" else "Configura Mistral para comenzar") },\n',
    '                    placeholder = { Text(if (configured) "Pregunta a TuNot…" else "Pregunta a TuNot… · modo local") },\n'
)
replace_once(
    p,
    '                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(enabled = configured && question.isNotBlank() && !busy, onClick = onSubmit),\n                    color = if (configured && question.isNotBlank() && !busy) NotCanBlue else NotCanGray.copy(alpha =',
    '                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(enabled = question.isNotBlank() && !busy, onClick = onSubmit),\n                    color = if (question.isNotBlank() && !busy) NotCanBlue else NotCanGray.copy(alpha ='
)
replace_once(
    p,
    '''        Text(if (configured) "Pregunta, resume o pídele a TuNot un mapa, tarjetas o cuestionario." else "Configura tu API key y Agent ID de Mistral desde Configuración.", color = NotCanGray)\n''',
    '''        Text(if (configured) "Pregunta, resume o pídele a TuNot un mapa, tarjetas o cuestionario." else "Modo local: usa tus apuntes y transcripciones para responder, crear mapas, tarjetas y cuestionarios sin Internet.", color = NotCanGray)\n'''
)
replace_once(
    p,
    '''private fun ChatBubble(\n    message: ChatMessage,\n''',
    '''private fun ChatBubble(\n    message: ChatMessage,\n'''
)
replace_once(
    p,
    '''    val user = message.role == ChatRole.USER\n    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {\n''',
    '''    val user = message.role == ChatRole.USER\n    val context = LocalContext.current\n    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {\n'''
)
replace_once(
    p,
    '''                if (safeVisibleContent.isNotBlank()) TuNotRichText(safeVisibleContent, color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)\n                message.mapArtifact?.let { artifact ->\n''',
    '''                if (safeVisibleContent.isNotBlank()) TuNotRichText(safeVisibleContent, color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)\n                if (!user && safeVisibleContent.isNotBlank()) {\n                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {\n                        TextButton(onClick = {\n                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager\n                            clipboard.setPrimaryClip(ClipData.newPlainText("Respuesta de TuNot", safeVisibleContent))\n                            Toast.makeText(context, "Respuesta copiada", Toast.LENGTH_SHORT).show()\n                        }) {\n                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))\n                            Spacer(Modifier.width(5.dp))\n                            Text("Copiar")\n                        }\n                    }\n                }\n                message.mapArtifact?.let { artifact ->\n'''
)
replace_once(
    p,
    '                modifier = Modifier.fillMaxWidth().clickable(enabled = configured && !busy) {\n',
    '                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) {\n'
)
replace_once(
    p,
    '                        Text(tool.title, color = if (configured) NotCanOffWhite else NotCanGray, fontWeight = FontWeight.Medium)\n',
    '                        Text(tool.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)\n'
)
replace_once(
    p,
    '        if (!configured) item { Text("Configura Mistral desde Configuración para activar estas herramientas.", color = NotCanGray) }\n',
    '        if (!configured) item { Text("Modo local activo: estos recursos se generan con el material guardado. Mistral mejora la elaboración cuando hay Internet, pero ya no es obligatorio.", color = NotCanGray) }\n'
)

print("0.8.15 quality patch applied successfully")
