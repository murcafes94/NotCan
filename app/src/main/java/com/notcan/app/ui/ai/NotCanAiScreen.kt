package com.notcan.app.ui.ai

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.notcan.app.ai.MistralCredentialsStore
import com.notcan.app.ai.NotCanAiService
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.DetectedCueEntity
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.localai.StudyModelState
import com.notcan.app.localai.WhisperModelSpec
import com.notcan.app.localai.WhisperModelState
import com.notcan.app.settings.NotCanPreferences
import com.notcan.app.ui.maps.ParsedStudyMapArtifact
import com.notcan.app.ui.maps.StudyMapArtifactParser
import com.notcan.app.ui.maps.StudyMapScreen
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface

@Composable
fun NotCanAiScreen(
    subjectName: String?,
    classTitle: String?,
    configured: Boolean,
    busy: Boolean,
    error: String?,
    result: String,
    transcripts: List<TranscriptEntity>,
    audioRecordings: List<AudioRecordingEntity>,
    detectedCues: List<DetectedCueEntity> = emptyList(),
    studyModelState: StudyModelState,
    studyModelProgress: Int?,
    whisperModelState: WhisperModelState,
    whisperModelProgress: Int?,
    localWhisperBusy: Boolean,
    localWhisperError: String?,
    onDownloadStudyModel: () -> Unit,
    onRemoveStudyModel: () -> Unit,
    onDownloadWhisperModel: () -> Unit,
    onRemoveWhisperModel: () -> Unit,
    onTranscribeLocal: (String) -> Unit,
    onAsk: (String) -> Unit,
    onClear: () -> Unit
) {
    @Suppress("UNUSED_VARIABLE")
    val legacy = listOf(configured, studyModelState, studyModelProgress, onDownloadStudyModel, onRemoveStudyModel)
    val context = LocalContext.current
    val preferences = remember(context) { NotCanPreferences(context.applicationContext) }
    val credentialStore = remember(context) { MistralCredentialsStore(context.applicationContext) }
    val onlineConfigured = credentialStore.hasApiKey() && preferences.mistralAgentId.isNotBlank()
    val artifactStore = remember(context) { TuNotArtifactStore(context.applicationContext) }
    val artifactScope = remember(subjectName, classTitle) { "${subjectName.orEmpty()}::${classTitle.orEmpty()}" }
    var artifactRevision by remember(artifactScope) { mutableIntStateOf(0) }
    var autoSaveNextArtifact by remember(artifactScope) { mutableStateOf(false) }
    var section by remember { mutableIntStateOf(1) }
    var openedMap by remember { mutableStateOf<ParsedStudyMapArtifact?>(null) }
    var openedDeck by remember { mutableStateOf<ParsedFlashcardArtifact?>(null) }
    var openedQuiz by remember { mutableStateOf<ParsedQuizArtifact?>(null) }

    LaunchedEffect(result, artifactScope) {
        if (autoSaveNextArtifact && result.isNotBlank()) {
            if (artifactStore.save(artifactScope, result) != null) {
                artifactRevision += 1
                autoSaveNextArtifact = false
            }
        }
    }

    openedMap?.let { artifact ->
        FullScreenStudyMap(artifact = artifact, onBack = { openedMap = null })
        return
    }
    openedDeck?.let { deck ->
        StudyFlashcardsScreen(deck = deck, onBack = { openedDeck = null })
        return
    }
    openedQuiz?.let { quiz ->
        StudyQuizScreen(quiz = quiz, onBack = { openedQuiz = null })
        return
    }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            when (section) {
                0 -> AiSources(
                    subjectName, classTitle, transcripts, audioRecordings, detectedCues,
                    whisperModelState, whisperModelProgress, localWhisperBusy, localWhisperError,
                    onDownloadWhisperModel, onRemoveWhisperModel, onTranscribeLocal
                )
                1 -> AiChat(
                    subjectName = subjectName,
                    classTitle = classTitle,
                    configured = onlineConfigured,
                    busy = busy,
                    error = error,
                    result = result,
                    onAsk = onAsk,
                    onClear = onClear,
                    onOpenMap = { openedMap = it },
                    onOpenDeck = { openedDeck = it },
                    onOpenQuiz = { openedQuiz = it },
                    onSaveArtifact = { raw ->
                        if (artifactStore.save(artifactScope, raw) != null) artifactRevision += 1
                    }
                )
                else -> AiStudio(
                    subjectName = subjectName,
                    classTitle = classTitle,
                    configured = onlineConfigured,
                    busy = busy,
                    artifactRevision = artifactRevision,
                    onAsk = { prompt, expectsArtifact ->
                        autoSaveNextArtifact = expectsArtifact
                        onAsk(prompt)
                        section = 1
                    },
                    onOpenMap = { openedMap = it },
                    onOpenDeck = { openedDeck = it },
                    onOpenQuiz = { openedQuiz = it },
                    onDeleteArtifact = { id ->
                        artifactStore.delete(artifactScope, id)
                        artifactRevision += 1
                    }
                )
            }
        }
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            NavigationBarItem(selected = section == 0, onClick = { section = 0 }, icon = { Icon(Icons.Default.Source, null) }, label = { Text("Fuentes") })
            NavigationBarItem(selected = section == 1, onClick = { section = 1 }, icon = { Icon(Icons.Default.Chat, null) }, label = { Text("Chat") })
            NavigationBarItem(selected = section == 2, onClick = { section = 2 }, icon = { Icon(Icons.Default.AutoAwesome, null) }, label = { Text("Estudio") })
        }
    }
}

@Composable
private fun FullScreenStudyMap(artifact: ParsedStudyMapArtifact, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver al chat", tint = NotCanOffWhite) }
                    Text("Mapa de estudio", color = NotCanGray, style = MaterialTheme.typography.labelLarge)
                }
                StudyMapScreen(
                    map = artifact.map,
                    initialLayout = artifact.preferredLayout,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AiSources(
    subjectName: String?,
    classTitle: String?,
    transcripts: List<TranscriptEntity>,
    audioRecordings: List<AudioRecordingEntity>,
    detectedCues: List<DetectedCueEntity>,
    whisperModelState: WhisperModelState,
    whisperModelProgress: Int?,
    localWhisperBusy: Boolean,
    localWhisperError: String?,
    onDownloadWhisperModel: () -> Unit,
    onRemoveWhisperModel: () -> Unit,
    onTranscribeLocal: (String) -> Unit
) {
    val latestAudio = audioRecordings.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(subjectName ?: "Fuentes", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(classTitle ?: "Selecciona una clase", color = NotCanGray)
            Spacer(Modifier.padding(top = 4.dp))
            Text("Tus apuntes, transcripciones y archivos están disponibles para TuNot cuando quieras usarlos como fuentes.", color = NotCanBlue)
        }
        item { AiExternalSourcesPanel(subjectName = subjectName, classTitle = classTitle, modifier = Modifier.fillMaxWidth()) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, null, tint = NotCanBlue)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("${WhisperModelSpec.DISPLAY_NAME} · transcripción final", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                            Text("Local y sin enviar el audio a Mistral", color = NotCanGray)
                        }
                    }
                    when (whisperModelState) {
                        WhisperModelState.NOT_INSTALLED -> Button(onClick = onDownloadWhisperModel) { Text("Descargar modelo") }
                        WhisperModelState.DOWNLOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(whisperModelProgress?.let { "Descargando… $it%" } ?: "Descargando…", color = NotCanGray)
                        }
                        WhisperModelState.INSTALLED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = latestAudio != null && !localWhisperBusy, onClick = { latestAudio?.let { onTranscribeLocal(it.id) } }) {
                                Text(if (localWhisperBusy) "En cola…" else "Transcribir último audio")
                            }
                            TextButton(onClick = onRemoveWhisperModel, enabled = !localWhisperBusy) { Text("Eliminar") }
                        }
                    }
                    localWhisperError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        item { Text("Material de esta clase", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { SourceRow(Icons.Default.GraphicEq, "Audios", "${audioRecordings.size} grabación(es)") }
        item { SourceRow(Icons.Default.Description, "Transcripciones", "${transcripts.size} texto(s) guardado(s)") }
        item { SourceRow(Icons.Default.AutoAwesome, "Señales académicas", "${detectedCues.size} señal(es)") }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.8f)), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = NotCanBlue)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                Text(subtitle, color = NotCanGray)
            }
        }
    }
}

private enum class ChatRole { USER, ASSISTANT }

private data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val rawContent: String = content,
    val mapArtifact: ParsedStudyMapArtifact? = null,
    val flashcards: ParsedFlashcardArtifact? = null,
    val quizArtifact: ParsedQuizArtifact? = null
)

private fun messageFromRaw(role: ChatRole, raw: String): ChatMessage {
    if (role == ChatRole.USER) return ChatMessage(role, raw, raw)
    val map = StudyMapArtifactParser.parse(raw)
    val deck = StudyFlashcardArtifactParser.parse(raw)
    val quiz = StudyQuizArtifactParser.parse(raw)
    val visible = when {
        map != null -> StudyMapArtifactParser.stripArtifact(raw)
        deck != null -> StudyFlashcardArtifactParser.stripArtifact(raw)
        quiz != null -> StudyQuizArtifactParser.stripArtifact(raw)
        else -> sanitizeUnparsedArtifact(raw)
    }
    return ChatMessage(role, visible, raw, map, deck, quiz)
}

private fun sanitizeUnparsedArtifact(raw: String): String {
    val looksStructured = raw.contains("NOTCAN_", ignoreCase = true) ||
        (raw.trimStart().startsWith("{") && (
            raw.contains("\"nodes\"") || raw.contains("\"cards\"") || raw.contains("\"questions\"")
        ))
    return if (looksStructured) {
        "TuNot generó un recurso de estudio, pero el formato llegó incompleto. Vuelve a generarlo para abrirlo de forma interactiva."
    } else raw
}

@Composable
private fun AiChat(
    subjectName: String?,
    classTitle: String?,
    configured: Boolean,
    busy: Boolean,
    error: String?,
    result: String,
    onAsk: (String) -> Unit,
    onClear: () -> Unit,
    onOpenMap: (ParsedStudyMapArtifact) -> Unit,
    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,
    onOpenQuiz: (ParsedQuizArtifact) -> Unit,
    onSaveArtifact: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { TuNotChatStore(context.applicationContext) }
    val scopeKey = remember(subjectName, classTitle) { "${subjectName.orEmpty()}::${classTitle.orEmpty()}" }
    var question by remember(scopeKey) { mutableStateOf("") }
    var sourceMode by remember(scopeKey) { mutableIntStateOf(1) } // 0 Mis fuentes · 1 Auto · 2 Web
    var socraticMode by remember(scopeKey) { mutableStateOf(false) }
    var toolsOpen by remember(scopeKey) { mutableStateOf(false) }
    val messages = remember(scopeKey) {
        mutableStateListOf<ChatMessage>().apply {
            store.load(scopeKey).forEach { stored ->
                val role = if (stored.role == "USER") ChatRole.USER else ChatRole.ASSISTANT
                add(messageFromRaw(role, stored.rawContent))
            }
        }
    }
    val listState = rememberLazyListState()

    fun persist() {
        store.save(
            scopeKey,
            messages.map { StoredTuNotMessage(it.role.name, it.rawContent) }
        )
    }

    LaunchedEffect(result) {
        if (result.isBlank()) return@LaunchedEffect
        val last = messages.lastOrNull()
        if (last?.role == ChatRole.ASSISTANT && last.rawContent == result) return@LaunchedEffect
        val message = messageFromRaw(ChatRole.ASSISTANT, result)
        messages.add(message)
        persist()
        message.mapArtifact?.let(onOpenMap)
        message.flashcards?.let(onOpenDeck)
        message.quizArtifact?.let(onOpenQuiz)
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun clearConversation() {
        messages.clear()
        store.clear(scopeKey)
        onClear()
    }

    fun submit() {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank() || busy || !configured) return
        messages.add(ChatMessage(ChatRole.USER, cleanQuestion))
        persist()
        val previousAssistant = messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.content.orEmpty()
        val prompt = buildString {
            when (sourceMode) {
                0 -> appendLine(NotCanAiService.SOURCE_ONLY_MARKER)
                2 -> appendLine(NotCanAiService.WEB_SEARCH_MARKER)
                else -> appendLine(NotCanAiService.AUTO_WEB_MARKER)
            }
            if (socraticMode) {
                appendLine(NotCanAiService.SOCRATIC_MARKER)
                if (previousAssistant.isNotBlank()) {
                    appendLine("ÚLTIMA INTERVENCIÓN DEL TUTOR:")
                    appendLine(previousAssistant)
                    appendLine("RESPUESTA DEL ESTUDIANTE:")
                }
            }
            append(cleanQuestion)
        }
        onAsk(prompt)
        question = ""
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 18.dp else 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CompactChatHeader(subjectName, classTitle, configured, toolsOpen) { toolsOpen = !toolsOpen }
            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnimatedVisibility(visible = toolsOpen) {
                        CompactAiTools(sourceMode, socraticMode, messages.isNotEmpty(), { sourceMode = it }, { socraticMode = it }, ::clearConversation, Modifier.width(250.dp))
                    }
                    ConversationPanel(configured, busy, error, messages, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact, Modifier.weight(1f).fillMaxSize())
                }
            } else {
                AnimatedVisibility(visible = toolsOpen) {
                    CompactAiTools(sourceMode, socraticMode, messages.isNotEmpty(), { sourceMode = it }, { socraticMode = it }, ::clearConversation, Modifier.fillMaxWidth())
                }
                ConversationPanel(configured, busy, error, messages, question, { question = it }, ::submit, listState, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CompactAiTools(
    sourceMode: Int,
    socraticMode: Boolean,
    hasMessages: Boolean,
    onSourceModeChange: (Int) -> Unit,
    onSocraticChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Fuentes de respuesta", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sourceMode == 0, onClick = { onSourceModeChange(0) }, label = { Text("Mis fuentes") })
                FilterChip(selected = sourceMode == 1, onClick = { onSourceModeChange(1) }, label = { Text("Auto") })
                FilterChip(selected = sourceMode == 2, onClick = { onSourceModeChange(2) }, label = { Text("Web") })
            }
            Text(
                when (sourceMode) {
                    0 -> "Solo material guardado en NotCan"
                    2 -> "Busca en DuckDuckGo antes de responder"
                    else -> "Busca solo cuando la pregunta necesita información externa o actual"
                },
                color = NotCanGray,
                style = MaterialTheme.typography.bodySmall
            )
            FilterChip(selected = socraticMode, onClick = { onSocraticChange(!socraticMode) }, label = { Text("Socrático") }, leadingIcon = { Icon(Icons.Default.Quiz, null) })
            if (hasMessages) TextButton(onClick = onClear) { Text("Nueva conversación") }
        }
    }
}

@Composable
private fun CompactChatHeader(subjectName: String?, classTitle: String?, configured: Boolean, toolsOpen: Boolean, onToggleTools: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(13.dp), color = NotCanBlue.copy(alpha = 0.13f)) {
            Icon(Icons.Default.AutoAwesome, null, tint = NotCanBlue, modifier = Modifier.padding(9.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("TuNot", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(subjectName, classTitle).joinToString(" · ").ifBlank { "Asistente académico" }, color = NotCanGray, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        ConnectionBadge(configured)
        IconButton(onClick = onToggleTools) { Icon(Icons.Default.Menu, if (toolsOpen) "Ocultar opciones" else "Opciones de TuNot", tint = NotCanBlue) }
    }
}

@Composable
private fun ConnectionBadge(configured: Boolean) {
    Surface(color = if (configured) NotCanBlue.copy(alpha = 0.13f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            if (configured) "Mistral" else "Sin configurar",
            color = if (configured) NotCanBlue else MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun ConversationPanel(
    configured: Boolean,
    busy: Boolean,
    error: String?,
    messages: List<ChatMessage>,
    question: String,
    onQuestionChange: (String) -> Unit,
    onSubmit: () -> Unit,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onOpenMap: (ParsedStudyMapArtifact) -> Unit,
    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,
    onOpenQuiz: (ParsedQuizArtifact) -> Unit,
    onSaveArtifact: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty()) item { EmptyConversation(configured) }
                items(messages) { message -> ChatBubble(message, onOpenMap, onOpenDeck, onOpenQuiz, onSaveArtifact) }
                if (busy) item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.width(17.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("TuNot está preparando la respuesta…", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let { message ->
                    item {
                        Surface(color = MaterialTheme.colorScheme.error.copy(alpha = 0.10f), shape = RoundedCornerShape(14.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            }
            Divider(color = NotCanGray.copy(alpha = 0.18f))
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = question,
                    onValueChange = onQuestionChange,
                    placeholder = { Text(if (configured) "Pregunta a TuNot…" else "Configura Mistral para comenzar") },
                    modifier = Modifier.weight(1f),
                    minLines = 1,
                    maxLines = 5,
                    shape = RoundedCornerShape(18.dp)
                )
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(enabled = configured && question.isNotBlank() && !busy, onClick = onSubmit),
                    color = if (configured && question.isNotBlank() && !busy) NotCanBlue else NotCanGray.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Icon(Icons.Default.Send, "Enviar", tint = if (configured && question.isNotBlank() && !busy) Color.White else NotCanGray, modifier = Modifier.padding(15.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyConversation(configured: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = RoundedCornerShape(18.dp), color = NotCanBlue.copy(alpha = 0.12f)) {
            Icon(Icons.Default.MenuBook, null, tint = NotCanBlue, modifier = Modifier.padding(14.dp))
        }
        Text("¿Qué estudiamos hoy?", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(if (configured) "Pregunta, resume o pídele a TuNot un mapa, tarjetas o cuestionario." else "Configura tu API key y Agent ID de Mistral desde Configuración.", color = NotCanGray)
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onOpenMap: (ParsedStudyMapArtifact) -> Unit,
    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,
    onOpenQuiz: (ParsedQuizArtifact) -> Unit,
    onSaveArtifact: (String) -> Unit
) {
    val user = message.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (user) NotCanBlue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (user) 18.dp else 5.dp, bottomEnd = if (user) 5.dp else 18.dp),
            modifier = Modifier.fillMaxWidth(if (user) 0.78f else 0.98f)
        ) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (user) "Tú" else "TuNot", color = if (user) NotCanBlue else NotCanGray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                val safeVisibleContent = if (user) message.content else sanitizeUnparsedArtifact(message.content)
                if (safeVisibleContent.isNotBlank()) TuNotRichText(safeVisibleContent, color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)
                message.mapArtifact?.let { artifact ->
                    ArtifactCard(
                        icon = Icons.Default.AutoAwesome,
                        title = artifact.map.title,
                        subtitle = "Mapa interactivo · pantalla completa",
                        action = "Abrir mapa",
                        onClick = { onOpenMap(artifact) },
                        onSave = { onSaveArtifact(message.rawContent) }
                    )
                }
                message.flashcards?.let { deck ->
                    ArtifactCard(
                        icon = Icons.Default.Style,
                        title = deck.title,
                        subtitle = "${deck.cards.size} tarjetas · repaso activo",
                        action = "Abrir tarjetas",
                        onClick = { onOpenDeck(deck) },
                        onSave = { onSaveArtifact(message.rawContent) }
                    )
                }
                message.quizArtifact?.let { quiz ->
                    ArtifactCard(
                        icon = Icons.Default.Quiz,
                        title = quiz.title,
                        subtitle = "${quiz.questions.size} preguntas · corrección y repaso de errores",
                        action = "Responder",
                        onClick = { onOpenQuiz(quiz) },
                        onSave = { onSaveArtifact(message.rawContent) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtifactCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    action: String,
    onClick: () -> Unit,
    onSave: (() -> Unit)? = null
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = NotCanBlue.copy(alpha = 0.12f)) {
                Icon(icon, null, tint = NotCanBlue, modifier = Modifier.padding(9.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
            }
            onSave?.let { save -> TextButton(onClick = save) { Text("Guardar") } }
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

private data class StudyTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val prompt: String,
    val marker: String? = null
)

@Composable
private fun AiStudio(
    subjectName: String?,
    classTitle: String?,
    configured: Boolean,
    busy: Boolean,
    artifactRevision: Int,
    onAsk: (String, Boolean) -> Unit,
    onOpenMap: (ParsedStudyMapArtifact) -> Unit,
    onOpenDeck: (ParsedFlashcardArtifact) -> Unit,
    onOpenQuiz: (ParsedQuizArtifact) -> Unit,
    onDeleteArtifact: (String) -> Unit
) {
    val context = LocalContext.current
    val store = remember(context) { TuNotArtifactStore(context.applicationContext) }
    val scope = remember(subjectName, classTitle) { "${subjectName.orEmpty()}::${classTitle.orEmpty()}" }
    val savedArtifacts = remember(scope, artifactRevision) { store.load(scope) }
    val options = listOf(
        StudyTool("Resumen de clase", "Ideas principales, conceptos y estructura", Icons.Default.GraphicEq, "Haz un resumen estructurado de esta clase. Separa ideas principales, conceptos clave, definiciones y relaciones."),
        StudyTool("Tarjetas didácticas", "Repaso activo, una pregunta por tarjeta", Icons.Default.Style, "Crea entre 12 y 20 tarjetas didácticas de esta clase.", NotCanAiService.FLASHCARDS_MARKER),
        StudyTool("Cuestionario", "Respóndelo aquí y repite los errores", Icons.Default.Quiz, "Crea un cuestionario mixto basado exclusivamente en esta clase. Combina opción múltiple, verdadero/falso y algunas preguntas breves de desarrollo.", NotCanAiService.QUIZ_MARKER),
        StudyTool("Mapa mental", "Ramas y subramas interactivas", Icons.Default.AutoAwesome, "Hazme un mapa mental de esta clase. Organiza el tema central, ramas principales y subramas. Usa solamente las fuentes disponibles."),
        StudyTool("Mapa conceptual", "Conceptos y relaciones etiquetadas", Icons.Default.Source, "Hazme un mapa conceptual de esta clase con conceptos, jerarquías y frases de enlace. Usa solamente las fuentes disponibles."),
        StudyTool("Mapa de ideas", "Presentación visual en tarjetas", Icons.Default.Description, "Hazme un mapa de ideas de esta clase, más visual y sencillo, usando tarjetas radiales. Usa solamente las fuentes disponibles.")
    )
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Herramientas de estudio", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("TuNot genera el material y NotCan lo guarda dentro de esta clase para reutilizarlo sin otra llamada al modelo.", color = NotCanGray)
        }
        items(options) { tool ->
            val expectsArtifact = tool.marker != null || tool.title.startsWith("Mapa")
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = configured && !busy) {
                    onAsk(
                        buildString {
                            appendLine(NotCanAiService.SOURCE_ONLY_MARKER)
                            tool.marker?.let { appendLine(it) }
                            append(tool.prompt)
                        },
                        expectsArtifact
                    )
                },
                colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(12.dp), color = NotCanBlue.copy(alpha = 0.12f)) {
                        Icon(tool.icon, null, tint = NotCanBlue, modifier = Modifier.padding(10.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(tool.title, color = if (configured) NotCanOffWhite else NotCanGray, fontWeight = FontWeight.Medium)
                        Text(tool.subtitle, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (!configured) item { Text("Configura Mistral desde Configuración para activar estas herramientas.", color = NotCanGray) }

        item {
            Spacer(Modifier.padding(top = 4.dp))
            Text("Guardado en esta clase", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                if (savedArtifacts.isEmpty()) "Los mapas, tarjetas y cuestionarios que generes desde aquí aparecerán en esta sección." else "${savedArtifacts.size} material(es) disponibles sin volver a generar.",
                color = NotCanGray,
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(savedArtifacts, key = { it.id }) { artifact ->
            val icon = when (artifact.kind) {
                StudyArtifactKind.MAP -> Icons.Default.AutoAwesome
                StudyArtifactKind.FLASHCARDS -> Icons.Default.Style
                StudyArtifactKind.QUIZ -> Icons.Default.Quiz
            }
            val kindLabel = when (artifact.kind) {
                StudyArtifactKind.MAP -> "Mapa"
                StudyArtifactKind.FLASHCARDS -> "Tarjetas"
                StudyArtifactKind.QUIZ -> "Cuestionario"
            }
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(icon, null, tint = NotCanBlue)
                    Column(Modifier.weight(1f)) {
                        Text(artifact.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                        Text(kindLabel, color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = {
                        when (artifact.kind) {
                            StudyArtifactKind.MAP -> StudyMapArtifactParser.parse(artifact.rawContent)?.let(onOpenMap)
                            StudyArtifactKind.FLASHCARDS -> StudyFlashcardArtifactParser.parse(artifact.rawContent)?.let(onOpenDeck)
                            StudyArtifactKind.QUIZ -> StudyQuizArtifactParser.parse(artifact.rawContent)?.let(onOpenQuiz)
                        }
                    }) { Text("Abrir") }
                    TextButton(onClick = { onDeleteArtifact(artifact.id) }) { Text("Eliminar") }
                }
            }
        }
    }
}
