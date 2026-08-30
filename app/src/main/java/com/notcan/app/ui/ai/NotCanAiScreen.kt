package com.notcan.app.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
    var section by remember { mutableIntStateOf(1) }

    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            when (section) {
                0 -> AiSources(
                    subjectName = subjectName,
                    classTitle = classTitle,
                    transcripts = transcripts,
                    audioRecordings = audioRecordings,
                    detectedCues = detectedCues,
                    whisperModelState = whisperModelState,
                    whisperModelProgress = whisperModelProgress,
                    localWhisperBusy = localWhisperBusy,
                    localWhisperError = localWhisperError,
                    onDownloadWhisperModel = onDownloadWhisperModel,
                    onRemoveWhisperModel = onRemoveWhisperModel,
                    onTranscribeLocal = onTranscribeLocal
                )
                1 -> AiChat(
                    subjectName = subjectName,
                    classTitle = classTitle,
                    configured = onlineConfigured,
                    busy = busy,
                    error = error,
                    result = result,
                    onAsk = onAsk,
                    onClear = onClear
                )
                else -> AiStudio(
                    configured = onlineConfigured,
                    busy = busy,
                    onAsk = { prompt -> onAsk(prompt); section = 1 }
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
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(subjectName ?: "Fuentes", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(classTitle ?: "Selecciona una clase", color = NotCanGray)
            Spacer(Modifier.height(4.dp))
            Text("Tus apuntes, transcripciones y archivos son el contexto de TuNot.", color = NotCanBlue)
        }

        item {
            AiExternalSourcesPanel(
                subjectName = subjectName,
                classTitle = classTitle,
                modifier = Modifier.fillMaxWidth()
            )
        }

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
                            CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
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
    val mapArtifact: ParsedStudyMapArtifact? = null
)

@Composable
private fun AiChat(
    subjectName: String?,
    classTitle: String?,
    configured: Boolean,
    busy: Boolean,
    error: String?,
    result: String,
    onAsk: (String) -> Unit,
    onClear: () -> Unit
) {
    var question by remember(classTitle) { mutableStateOf("") }
    var sourceOnly by remember(classTitle) { mutableStateOf(true) }
    var socraticMode by remember(classTitle) { mutableStateOf(false) }
    var toolsOpen by remember(classTitle) { mutableStateOf(false) }
    val messages = remember(classTitle) { mutableStateListOf<ChatMessage>() }
    val listState = rememberLazyListState()

    LaunchedEffect(result) {
        if (result.isBlank() || messages.lastOrNull()?.rawContent == result) return@LaunchedEffect
        val artifact = StudyMapArtifactParser.parse(result)
        val visibleText = if (artifact != null) StudyMapArtifactParser.stripArtifact(result) else result
        messages.add(
            ChatMessage(
                role = ChatRole.ASSISTANT,
                content = visibleText,
                rawContent = result,
                mapArtifact = artifact
            )
        )
    }

    LaunchedEffect(messages.size, busy) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun clearConversation() {
        messages.clear()
        onClear()
    }

    fun submit() {
        val cleanQuestion = question.trim()
        if (cleanQuestion.isBlank() || busy || !configured) return
        messages.add(ChatMessage(ChatRole.USER, cleanQuestion))
        val previousAssistant = messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.content.orEmpty()
        val prompt = buildString {
            if (sourceOnly) appendLine(NotCanAiService.SOURCE_ONLY_MARKER)
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
        Column(
            Modifier.fillMaxSize().padding(horizontal = if (wide) 18.dp else 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CompactChatHeader(
                subjectName = subjectName,
                classTitle = classTitle,
                configured = configured,
                toolsOpen = toolsOpen,
                onToggleTools = { toolsOpen = !toolsOpen }
            )

            if (wide) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    AnimatedVisibility(visible = toolsOpen) {
                        CompactAiTools(
                            sourceOnly = sourceOnly,
                            socraticMode = socraticMode,
                            hasMessages = messages.isNotEmpty(),
                            onSourceOnlyChange = { sourceOnly = it },
                            onSocraticChange = { socraticMode = it },
                            onClear = ::clearConversation,
                            modifier = Modifier.width(250.dp)
                        )
                    }
                    ConversationPanel(
                        configured = configured,
                        busy = busy,
                        error = error,
                        messages = messages,
                        question = question,
                        onQuestionChange = { question = it },
                        onSubmit = ::submit,
                        listState = listState,
                        modifier = Modifier.weight(1f).fillMaxSize()
                    )
                }
            } else {
                AnimatedVisibility(visible = toolsOpen) {
                    CompactAiTools(
                        sourceOnly = sourceOnly,
                        socraticMode = socraticMode,
                        hasMessages = messages.isNotEmpty(),
                        onSourceOnlyChange = { sourceOnly = it },
                        onSocraticChange = { socraticMode = it },
                        onClear = ::clearConversation,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ConversationPanel(
                    configured = configured,
                    busy = busy,
                    error = error,
                    messages = messages,
                    question = question,
                    onQuestionChange = { question = it },
                    onSubmit = ::submit,
                    listState = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompactAiTools(
    sourceOnly: Boolean,
    socraticMode: Boolean,
    hasMessages: Boolean,
    onSourceOnlyChange: (Boolean) -> Unit,
    onSocraticChange: (Boolean) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f)), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sourceOnly, onClick = { onSourceOnlyChange(!sourceOnly) }, label = { Text("Solo mis fuentes") }, leadingIcon = { Icon(Icons.Default.Source, null) })
            FilterChip(selected = socraticMode, onClick = { onSocraticChange(!socraticMode) }, label = { Text("Socrático") }, leadingIcon = { Icon(Icons.Default.Quiz, null) })
            if (hasMessages) TextButton(onClick = onClear) { Text("Nueva conversación") }
        }
    }
}

@Composable
private fun CompactChatHeader(
    subjectName: String?,
    classTitle: String?,
    configured: Boolean,
    toolsOpen: Boolean,
    onToggleTools: () -> Unit
) {
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
    Surface(
        color = if (configured) NotCanBlue.copy(alpha = 0.13f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
        shape = RoundedCornerShape(50)
    ) {
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
                items(messages) { message -> ChatBubble(message) }
                if (busy) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            CircularProgressIndicator(modifier = Modifier.width(17.dp).height(17.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("TuNot está preparando la respuesta…", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                        }
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
    Column(
        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(shape = RoundedCornerShape(18.dp), color = NotCanBlue.copy(alpha = 0.12f)) {
            Icon(Icons.Default.MenuBook, null, tint = NotCanBlue, modifier = Modifier.padding(14.dp))
        }
        Text("¿Qué estudiamos hoy?", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            if (configured) "Pregunta, resume o pídele a TuNot un mapa mental, conceptual o de ideas."
            else "Configura tu API key y Agent ID de Mistral desde Configuración.",
            color = NotCanGray
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val user = message.role == ChatRole.USER
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (user) NotCanBlue.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (user) 18.dp else 5.dp,
                bottomEnd = if (user) 5.dp else 18.dp
            ),
            modifier = Modifier.fillMaxWidth(if (user) 0.78f else 0.98f)
        ) {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (user) "Tú" else "TuNot", color = if (user) NotCanBlue else NotCanGray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                if (message.content.isNotBlank()) {
                    Text(message.content, color = NotCanOffWhite, style = MaterialTheme.typography.bodyMedium)
                }
                message.mapArtifact?.let { artifact ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        StudyMapScreen(
                            map = artifact.map,
                            initialLayout = artifact.preferredLayout,
                            modifier = Modifier.fillMaxWidth().height(430.dp)
                        )
                    }
                    Text(
                        "Mapa interactivo · toca una rama para contraerla o expandirla · pellizca para zoom · usa ⋮ para compartir PNG/PDF",
                        color = NotCanGray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

private data class StudyTool(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val prompt: String
)

@Composable
private fun AiStudio(configured: Boolean, busy: Boolean, onAsk: (String) -> Unit) {
    val options = listOf(
        StudyTool("Resumen de clase", "Ideas principales, conceptos y estructura", Icons.Default.GraphicEq, "Haz un resumen estructurado de esta clase. Separa ideas principales, conceptos clave, definiciones y relaciones."),
        StudyTool("Tarjetas didácticas", "Pregunta-respuesta para repaso activo", Icons.Default.Style, "Crea entre 12 y 20 tarjetas didácticas basadas exclusivamente en esta clase."),
        StudyTool("Cuestionario", "Opción múltiple y desarrollo", Icons.Default.Quiz, "Crea un cuestionario basado exclusivamente en esta clase. Incluye opción múltiple y desarrollo, con respuestas separadas al final."),
        StudyTool("Mapa mental", "Ramas y subramas interactivas", Icons.Default.AutoAwesome, "Hazme un mapa mental de esta clase. Organiza el tema central, ramas principales y subramas. Usa solamente las fuentes disponibles."),
        StudyTool("Mapa conceptual", "Conceptos y relaciones etiquetadas", Icons.Default.Source, "Hazme un mapa conceptual de esta clase con conceptos, jerarquías y frases de enlace. Usa solamente las fuentes disponibles."),
        StudyTool("Mapa de ideas", "Presentación visual en tarjetas", Icons.Default.Description, "Hazme un mapa de ideas de esta clase, más visual y sencillo, usando tarjetas radiales. Usa solamente las fuentes disponibles.")
    )

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Herramientas de estudio", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Convierte tus fuentes en material listo para estudiar.", color = NotCanGray)
        }
        items(options) { tool ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = configured && !busy) {
                    onAsk("${NotCanAiService.SOURCE_ONLY_MARKER}\n${tool.prompt}")
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
    }
}
