package com.notcan.app.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    // Legacy local-model arguments are intentionally retained in the public signature during
    // the 0.7.7 migration so MainActivity remains binary/simple-source compatible.
    @Suppress("UNUSED_VARIABLE") val legacy = listOf(configured, studyModelState, studyModelProgress, onDownloadStudyModel, onRemoveStudyModel)

    val context = LocalContext.current
    val preferences = remember(context) { NotCanPreferences(context.applicationContext) }
    val credentialStore = remember(context) { MistralCredentialsStore(context.applicationContext) }
    val onlineConfigured = credentialStore.hasApiKey() && preferences.mistralAgentId.isNotBlank()
    var section by remember { mutableIntStateOf(1) }

    Column(Modifier.fillMaxSize()) {
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
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(subjectName ?: "Fuentes", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(classTitle ?: "Selecciona una clase", color = NotCanGray)
            Text("Tus apuntes y transcripciones siguen siendo la base del asistente. Mistral solo se usa cuando haces una consulta de IA.", color = NotCanBlue)
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f)), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Respuesta centrada en tus fuentes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("Activa “Solo mis fuentes” para impedir que el agente complete huecos con conocimiento general.", color = NotCanGray)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, null, tint = NotCanBlue)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${WhisperModelSpec.DISPLAY_NAME} · transcripción final", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                            Text("Transcripción local · sin usar Mistral", color = NotCanGray)
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
                            TextButton(onClick = onRemoveWhisperModel, enabled = !localWhisperBusy) { Text("Eliminar modelo") }
                        }
                    }
                    localWhisperError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        item { Text("Material de esta clase", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        item { SourceRow(Icons.Default.GraphicEq, "Audios", "${audioRecordings.size} grabación(es)") }
        item { SourceRow(Icons.Default.Description, "Transcripciones", "${transcripts.size} texto(s) guardado(s)") }
        item { SourceRow(Icons.Default.AutoAwesome, "Señales académicas", "${detectedCues.size} tarea(s), examen(es) o énfasis detectado(s)") }
        if (detectedCues.isNotEmpty()) {
            items(detectedCues.take(12), key = { it.id }) { cue ->
                Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(cue.label, color = NotCanBlue, fontWeight = FontWeight.SemiBold)
                        Text(cue.excerpt, color = NotCanOffWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(icon: ImageVector, title: String, subtitle: String) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.8f))) {
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

    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = NotCanBlue)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("NotCan AI", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(subjectName, classTitle).joinToString(" · ").ifBlank { "Asistente académico · Mistral" }, color = NotCanGray)
            }
            if (result.isNotBlank()) TextButton(onClick = onClear) { Text("Limpiar") }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.72f)), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (configured) "Mistral conectado" else "Mistral no configurado", color = if (configured) NotCanBlue else MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                Text(if (configured) "Usando tu agente de Mistral. La API key permanece cifrada en este dispositivo." else "Abre Configuración → Asistente NotCan e introduce tu API key y Agent ID.", color = NotCanGray)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = sourceOnly, onClick = { sourceOnly = !sourceOnly }, label = { Text("Solo mis fuentes") }, leadingIcon = { Icon(Icons.Default.Source, null) })
            FilterChip(selected = socraticMode, onClick = { socraticMode = !socraticMode }, label = { Text("Socrático") }, leadingIcon = { Icon(Icons.Default.Quiz, null) })
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                if (result.isBlank()) {
                    Icon(Icons.Default.MenuBook, null, tint = NotCanBlue)
                    Spacer(Modifier.height(8.dp))
                    Text("Pregunta, resume, prepara un examen o transforma tus fuentes en material de estudio.", color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                    Text("El asistente generativo es online; grabación, apuntes y transcripción local siguen disponibles sin conexión.", color = NotCanGray)
                } else {
                    Text(result, color = NotCanOffWhite)
                }
                if (busy) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Consultando tu agente de Mistral…", color = NotCanGray)
                    }
                }
                error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            label = { Text(if (socraticMode && result.isNotBlank()) "Tu respuesta…" else "Pregunta o tema…") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2
        )
        Button(
            enabled = configured && question.isNotBlank() && !busy,
            onClick = {
                val prompt = buildString {
                    if (sourceOnly) appendLine(NotCanAiService.SOURCE_ONLY_MARKER)
                    if (socraticMode) {
                        appendLine(NotCanAiService.SOCRATIC_MARKER)
                        if (result.isNotBlank()) {
                            appendLine("ÚLTIMA INTERVENCIÓN DEL TUTOR:")
                            appendLine(result)
                            appendLine("RESPUESTA DEL ESTUDIANTE:")
                        }
                    }
                    append(question)
                }
                onAsk(prompt)
                question = ""
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (socraticMode) "Continuar tutoría" else "Preguntar") }
    }
}

private data class StudyTool(val title: String, val subtitle: String, val icon: ImageVector, val prompt: String)

@Composable
private fun AiStudio(configured: Boolean, busy: Boolean, onAsk: (String) -> Unit) {
    val options = listOf(
        StudyTool("Resumen de clase", "Ideas principales, conceptos y estructura", Icons.Default.GraphicEq, "Haz un resumen estructurado de esta clase. Separa ideas principales, conceptos clave, definiciones y relaciones. No agregues contenido que no aparezca en las fuentes."),
        StudyTool("Tarjetas didácticas", "Pregunta-respuesta para repaso activo", Icons.Default.Style, "Crea entre 12 y 20 tarjetas didácticas basadas exclusivamente en esta clase."),
        StudyTool("Cuestionario", "Opción múltiple y desarrollo", Icons.Default.Quiz, "Crea un cuestionario basado exclusivamente en esta clase. Incluye opción múltiple y desarrollo, con respuestas separadas al final."),
        StudyTool("Preparar examen oral", "Preguntas progresivas y puntos clave", Icons.Default.MenuBook, "Prepara un examen oral usando solo estas fuentes: organiza temas, formula preguntas progresivas y da puntos clave de una respuesta correcta."),
        StudyTool("Mapa mental", "Tema central, ramas y subramas", Icons.Default.AutoAwesome, "Genera una estructura de mapa mental editable: tema central, ramas principales, subramas y conexiones. Usa solamente las fuentes disponibles."),
        StudyTool("Mapa conceptual", "Conceptos, enlaces y relaciones cruzadas", Icons.Default.Source, "Genera una estructura de mapa conceptual: conceptos, relaciones con frases de enlace, jerarquías y relaciones cruzadas. Usa solamente las fuentes disponibles."),
        StudyTool("Comprobar fuentes", "Distingue respaldo y datos no verificables", Icons.Default.Description, "Extrae las afirmaciones académicas importantes y comprueba si están respaldadas por apuntes, transcripción o ambas. Marca como 'No consta' lo que no pueda verificarse.")
    )

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Estudio", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Transforma tus materiales con tu agente de Mistral.", color = NotCanGray)
        }
        items(options) { tool ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = configured && !busy) { onAsk("${NotCanAiService.SOURCE_ONLY_MARKER}\n${tool.prompt}") },
                colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(tool.icon, null, tint = NotCanBlue)
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
