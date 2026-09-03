from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    file = ROOT / path
    text = file.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:100]!r}")
    text = text.replace(old, new, 1)
    file.write_text(text, encoding="utf-8")


# 1) LiteRT Gemma: expose incremental output and adapt source context to question scope.
engine = "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt"
replace_once(
    engine,
    """        question: String,\n        strictSources: Boolean\n    ): Answer = mutex.withLock {""",
    """        question: String,\n        strictSources: Boolean,\n        onPartial: ((text: String, backendLabel: String) -> Unit)? = null\n    ): Answer = mutex.withLock {""",
)
replace_once(
    engine,
    """                    conversation.sendMessageAsync(prompt).collect { message ->\n                        output.append(message.toString())\n                    }""",
    """                    conversation.sendMessageAsync(prompt).collect { message ->\n                        val delta = message.toString()\n                        if (delta.isNotEmpty()) {\n                            output.append(delta)\n                            onPartial?.invoke(output.toString(), engineHolder.backendLabel)\n                        }\n                    }""",
)
replace_once(
    engine,
    """        val tokens = queryTokens(question)\n        val broadRequest = isBroadSourceRequest(question)\n        val scored = chunks.map { chunk ->\n            chunk.copy(score = scoreChunk(chunk.text, tokens))\n        }\n\n        val selected = when {\n            broadRequest -> evenlySample(scored, MAX_SELECTED_CHUNKS)\n            tokens.isNotEmpty() -> scored\n                .filter { it.score > 0 }\n                .sortedByDescending { it.score }\n                .take(MAX_SELECTED_CHUNKS)\n            else -> emptyList()\n        }.ifEmpty {\n            // In strict mode retain representative material so paraphrases can still be found.\n            // In free mode avoid injecting unrelated class material into a general question.\n            if (strictSources) evenlySample(scored, FALLBACK_SELECTED_CHUNKS) else emptyList()\n        }\n\n        if (selected.isEmpty()) return \"\"\n        return buildString {\n            subjectName?.takeIf { it.isNotBlank() }?.let { appendLine(\"Materia: $it\") }\n            selected.forEachIndexed { index, chunk ->\n                if (index > 0) appendLine()\n                appendLine(\"[${chunk.label}]\")\n                appendLine(chunk.text)\n            }\n        }.take(MAX_SOURCE_CHARS)""",
    """        val tokens = queryTokens(question)\n        val broadRequest = isBroadSourceRequest(question)\n        val sourceOverviewRequest = isSourceOverviewRequest(question)\n        val scored = chunks.map { chunk ->\n            chunk.copy(score = scoreChunk(chunk.text, tokens))\n        }\n\n        val selected = when {\n            broadRequest -> evenlySample(scored, BROAD_SELECTED_CHUNKS)\n            sourceOverviewRequest && tokens.isNotEmpty() -> scored\n                .filter { it.score > 0 }\n                .sortedByDescending { it.score }\n                .take(OVERVIEW_SELECTED_CHUNKS)\n            tokens.isNotEmpty() -> scored\n                .filter { it.score > 0 }\n                .sortedByDescending { it.score }\n                .take(FOCUSED_SELECTED_CHUNKS)\n            else -> emptyList()\n        }.ifEmpty {\n            // A strict-source question may be a paraphrase with no exact lexical hit.\n            // Keep a small representative fallback instead of paying the cost of the whole class.\n            if (strictSources) evenlySample(scored, FOCUSED_SELECTED_CHUNKS) else emptyList()\n        }\n\n        if (selected.isEmpty()) return \"\"\n        val sourceCharLimit = when {\n            broadRequest -> MAX_BROAD_SOURCE_CHARS\n            sourceOverviewRequest -> MAX_OVERVIEW_SOURCE_CHARS\n            else -> MAX_FOCUSED_SOURCE_CHARS\n        }\n        return buildString {\n            subjectName?.takeIf { it.isNotBlank() }?.let { appendLine(\"Materia: $it\") }\n            selected.forEachIndexed { index, chunk ->\n                if (index > 0) appendLine()\n                appendLine(\"[${chunk.label}]\")\n                appendLine(chunk.text)\n            }\n        }.take(sourceCharLimit)""",
)
replace_once(
    engine,
    """    private fun evenlySample(chunks: List<SourceChunk>, limit: Int): List<SourceChunk> {""",
    """    private fun isSourceOverviewRequest(question: String): Boolean {\n        val n = normalize(question)\n        return listOf(\n            \"de que habla\", \"de que trata\", \"que trata\", \"resumen de la fuente\",\n            \"resume la fuente\", \"resume el documento\", \"resume el archivo\"\n        ).any(n::contains)\n    }\n\n    private fun evenlySample(chunks: List<SourceChunk>, limit: Int): List<SourceChunk> {""",
)
replace_once(
    engine,
    """        private const val MAX_SOURCE_CHARS = 8_500\n        private const val SOURCE_CHUNK_CHARS = 1_000\n        private const val SOURCE_CHUNK_OVERLAP = 160\n        private const val MAX_SELECTED_CHUNKS = 7\n        private const val FALLBACK_SELECTED_CHUNKS = 5""",
    """        private const val MAX_BROAD_SOURCE_CHARS = 8_500\n        private const val MAX_OVERVIEW_SOURCE_CHARS = 5_500\n        private const val MAX_FOCUSED_SOURCE_CHARS = 3_400\n        private const val SOURCE_CHUNK_CHARS = 1_000\n        private const val SOURCE_CHUNK_OVERLAP = 160\n        private const val BROAD_SELECTED_CHUNKS = 7\n        private const val OVERVIEW_SELECTED_CHUNKS = 5\n        private const val FOCUSED_SELECTED_CHUNKS = 3""",
)

# 2) AI service: propagate Gemma partials while preserving all existing fallbacks.
service = "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
replace_once(
    service,
    """        transcript: String,\n        question: String\n    ): String {""",
    """        transcript: String,\n        question: String,\n        onPartial: ((String) -> Unit)? = null\n    ): String {""",
)
replace_once(
    service,
    """                        question = localQuestion,\n                        strictSources = strictSources\n                    )""",
    """                        question = localQuestion,\n                        strictSources = strictSources,\n                        onPartial = { partialText, backendLabel ->\n                            onPartial?.invoke(markEngine(\"Gemma 4 local · $backendLabel\", partialText))\n                        }\n                    )""",
)
replace_once(
    service,
    """                } catch (t: Throwable) {\n                    preferences.lastLocalAiError = \"Gemma 4: ${t.message ?: t.javaClass.simpleName}\"\n                }""",
    """                } catch (t: Throwable) {\n                    onPartial?.invoke(\"\")\n                    preferences.lastLocalAiError = \"Gemma 4: ${t.message ?: t.javaClass.simpleName}\"\n                }""",
)

# 3) ViewModel: publish partials into the existing result StateFlow.
view_model = "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt"
replace_once(
    view_model,
    """                _aiResult.value = aiService.studyAssistant(subjectName, notesText, transcriptText, question)\n                _aiConfigured.value = true""",
    """                val finalResult = aiService.studyAssistant(\n                    subjectName = subjectName,\n                    notes = notesText,\n                    transcript = transcriptText,\n                    question = question,\n                    onPartial = { partial -> _aiResult.value = partial }\n                )\n                _aiResult.value = finalResult\n                _aiConfigured.value = true""",
)

# 4) Compose chat: render one transient assistant bubble while busy; persist only the final result.
screen = "app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt"
replace_once(
    screen,
    """    var socraticMode by rememberSaveable(scopeKey) { mutableStateOf(false) }\n    var toolsOpen by remember(scopeKey) { mutableStateOf(false) }\n    val messages = remember(scopeKey) {""",
    """    var socraticMode by rememberSaveable(scopeKey) { mutableStateOf(false) }\n    var toolsOpen by remember(scopeKey) { mutableStateOf(false) }\n    var streamingMessage by remember(scopeKey) { mutableStateOf<ChatMessage?>(null) }\n    var submitLocked by remember(scopeKey) { mutableStateOf(false) }\n    val messages = remember(scopeKey) {""",
)
replace_once(
    screen,
    """    LaunchedEffect(result) {\n        if (result.isBlank()) return@LaunchedEffect\n        val last = messages.lastOrNull()\n        if (last?.role == ChatRole.ASSISTANT && last.rawContent == result) return@LaunchedEffect\n        val message = messageFromRaw(ChatRole.ASSISTANT, result)\n        messages.add(message)\n        persist()\n        message.mapArtifact?.let(onOpenMap)\n        message.flashcards?.let(onOpenDeck)\n        message.quizArtifact?.let(onOpenQuiz)\n    }\n\n    LaunchedEffect(messages.size, busy) {\n        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)\n    }""",
    """    LaunchedEffect(result, busy) {\n        if (busy) {\n            streamingMessage = result.takeIf { it.isNotBlank() }\n                ?.let { messageFromRaw(ChatRole.ASSISTANT, it) }\n            return@LaunchedEffect\n        }\n\n        if (result.isBlank()) {\n            streamingMessage = null\n            return@LaunchedEffect\n        }\n\n        val message = messageFromRaw(ChatRole.ASSISTANT, result)\n        val last = messages.lastOrNull()\n        if (!(last?.role == ChatRole.ASSISTANT && last.rawContent == result)) {\n            messages.add(message)\n            persist()\n            message.mapArtifact?.let(onOpenMap)\n            message.flashcards?.let(onOpenDeck)\n            message.quizArtifact?.let(onOpenQuiz)\n        }\n        streamingMessage = null\n    }\n\n    LaunchedEffect(busy) {\n        if (!busy) submitLocked = false\n    }\n\n    LaunchedEffect(messages.size, streamingMessage != null, busy) {\n        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)\n    }""",
)
replace_once(
    screen,
    """    fun clearConversation() {\n        messages.clear()\n        store.clear(scopeKey)\n        onClear()\n    }""",
    """    fun clearConversation() {\n        messages.clear()\n        streamingMessage = null\n        submitLocked = false\n        store.clear(scopeKey)\n        onClear()\n    }""",
)
replace_once(
    screen,
    """        val cleanQuestion = question.trim()\n        if (cleanQuestion.isBlank() || busy) return\n        messages.add(ChatMessage(ChatRole.USER, cleanQuestion))""",
    """        val cleanQuestion = question.trim()\n        if (cleanQuestion.isBlank() || busy || submitLocked) return\n        submitLocked = true\n        messages.add(ChatMessage(ChatRole.USER, cleanQuestion))""",
)
replace_once(
    screen,
    """            val actualEngine = messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.engineLabel\n                ?: if (configured) \"Automático\" else \"Local\"""",
    """            val effectiveBusy = busy || submitLocked\n            val actualEngine = streamingMessage?.engineLabel\n                ?: messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.engineLabel\n                ?: if (configured) \"Automático\" else \"Local\"""",
)
replace_once(
    screen,
    """ConversationPanel(configured, busy, error, messages, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact, Modifier.weight(1f).fillMaxSize())""",
    """ConversationPanel(configured, effectiveBusy, error, messages, streamingMessage, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact, Modifier.weight(1f).fillMaxSize())""",
)
replace_once(
    screen,
    """ConversationPanel(configured, busy, error, messages, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact, Modifier.weight(1f).fillMaxWidth())""",
    """ConversationPanel(configured, effectiveBusy, error, messages, streamingMessage, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact, Modifier.weight(1f).fillMaxWidth())""",
)
replace_once(
    screen,
    """    error: String?,\n    messages: List<ChatMessage>,\n    question: String,""",
    """    error: String?,\n    messages: List<ChatMessage>,\n    streamingMessage: ChatMessage?,\n    question: String,""",
)
replace_once(
    screen,
    """                items(messages) { message -> ChatBubble(message, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact) }\n                if (busy) item {\n                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {\n                        CircularProgressIndicator(modifier = Modifier.width(17.dp), strokeWidth = 2.dp)\n                        Spacer(Modifier.width(8.dp))\n                        Text(\"TuNot está preparando la respuesta…\", color = NotCanGray, style = MaterialTheme.typography.bodySmall)\n                    }\n                }""",
    """                items(messages) { message -> ChatBubble(message, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact) }\n                streamingMessage?.let { partial ->\n                    item(key = \"tunot-streaming\") {\n                        ChatBubble(partial, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact)\n                    }\n                }\n                if (busy && streamingMessage == null) item {\n                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {\n                        CircularProgressIndicator(modifier = Modifier.width(17.dp), strokeWidth = 2.dp)\n                        Spacer(Modifier.width(8.dp))\n                        Text(\"TuNot está preparando la respuesta…\", color = NotCanGray, style = MaterialTheme.typography.bodySmall)\n                    }\n                }""",
)

print("Gemma streaming + adaptive RAG patch applied successfully")
