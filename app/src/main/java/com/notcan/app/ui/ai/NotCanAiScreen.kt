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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.DetectedCueEntity
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.localai.StudyModelSpec
import com.notcan.app.localai.StudyModelState
import com.notcan.app.localai.WhisperModelSpec
import com.notcan.app.localai.WhisperModelState
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
    var section by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize()) {
        when (section) {
            0 -> AiSources(
                subjectName = subjectName,
                classTitle = classTitle,
                transcripts = transcripts,
                audioRecordings = audioRecordings,
                detectedCues = detectedCues,
                studyModelState = studyModelState,
                studyModelProgress = studyModelProgress,
                whisperModelState = whisperModelState,
                whisperModelProgress = whisperModelProgress,
                localWhisperBusy = localWhisperBusy,
                localWhisperError = localWhisperError,
                onDownloadStudyModel = onDownloadStudyModel,
                onRemoveStudyModel = onRemoveStudyModel,
                onDownloadWhisperModel = onDownloadWhisperModel,
                onRemoveWhisperModel = onRemoveWhisperModel,
                onTranscribeLocal = onTranscribeLocal
            )
            1 -> AiChat(subjectName, classTitle, configured, busy, error, result, onAsk, onClear, onDownloadStudyModel)
            else -> AiStudio(configured, busy, onAsk)
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
    onTranscribeLocal: (String) -> Unit
) {
    val latestAudio = audioRecordings.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(subjectName ?: "Fuentes", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(classTitle ?: "Selecciona una clase", color = NotCanGray)
            Text("Todos los modelos de esta pantalla funcionan localmente. No usan API ni consumen tokens.", color = NotCanBlue)
        }

        item {
            ModelCard(
                icon = Icons.Default.AutoAwesome,
                title = StudyModelSpec.DISPLAY_NAME,
                subtitle = "Tutor, resúmenes, cuestionarios y material de estudio · ~1,1 GB",
                stateLabel = when (studyModelState) {
                    StudyModelState.NOT_INSTALLED -> "No descargado"
                    StudyModelState.DOWNLOADING -> studyModelProgress?.let { "Descargando… $it%" } ?: "Descargando en segundo plano…"
                    StudyModelState.INSTALLED -> "Instalado · listo offline"
                },
                installed = studyModelState == StudyModelState.INSTALLED,
                downloading = studyModelState == StudyModelState.DOWNLOADING,
                onDownload = onDownloadStudyModel,
                onRemove = onRemoveStudyModel
            )
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, null, tint = NotCanBlue)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("${WhisperModelSpec.DISPLAY_NAME} · transcripción final", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                            Text("Alta calidad local · ~1,5 GB · sin tokens", color = NotCanGray)
                        }
                    }
                    when (whisperModelState) {
                        WhisperModelState.NOT_INSTALLED -> Button(onClick = onDownloadWhisperModel) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Descargar ~1,5 GB") }
                        WhisperModelState.DOWNLOADING -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp)); Text(whisperModelProgress?.let { "Descargando… $it%" } ?: "Descargando en segundo plano…", color = NotCanGray)
                        }
                        WhisperModelState.INSTALLED -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(enabled = latestAudio != null && !localWhisperBusy, onClick = { latestAudio?.let { onTranscribeLocal(it.id) } }) {
                                Text(if (localWhisperBusy) "En cola…" else "Transcribir último audio")
                            }
                            OutlinedButton(onClick = onRemoveWhisperModel, enabled = !localWhisperBusy) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(5.dp)); Text("Modelo") }
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
private fun ModelCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    stateLabel: String,
    installed: Boolean,
    downloading: Boolean,
    onDownload: () -> Unit,
    onRemove: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = NotCanBlue)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, color = NotCanGray)
                }
            }
            Text(stateLabel, color = if (installed) NotCanBlue else NotCanGray)
            when {
                downloading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("Puedes cerrar NotCan; Android continuará la descarga.", color = NotCanGray)
                }
                installed -> OutlinedButton(onClick = onRemove) { Icon(Icons.Default.Delete, null); Spacer(Modifier.width(5.dp)); Text("Eliminar modelo") }
                else -> Button(onClick = onDownload) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Descargar") }
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
            Column { Text(title, color = NotCanOffWhite, fontWeight = FontWeight.Medium); Text(subtitle, color = NotCanGray) }
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
    onClear: () -> Unit,
    onDownloadModel: () -> Unit
) {
    var question by remember(classTitle) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, null, tint = NotCanBlue)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("NotCan AI", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(subjectName, classTitle).joinToString(" · ").ifBlank { "Tutor académico local" }, color = NotCanGray)
            }
            if (result.isNotBlank()) TextButton(onClick = onClear) { Text("Nuevo") }
        }

        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.weight(1f)) {
            Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                if (result.isBlank()) {
                    Icon(Icons.Default.MenuBook, null, tint = NotCanBlue)
                    Spacer(Modifier.height(8.dp))
                    Text("Pregunta usando tus apuntes y transcripciones como fuentes.", color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                    Text("El modelo se ejecuta en tu dispositivo. Puedes personalizar nombre, profundidad y estilo desde Configuración.", color = NotCanGray)
                } else Text(result, color = NotCanOffWhite)
                error?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }

        if (!configured) {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface.copy(alpha = 0.75f))) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("NotCan AI aún no está descargada", color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                    Text("Descarga DeepSeek R1 1.5B (~1,1 GB) una sola vez. Después funciona offline y sin tokens.", color = NotCanGray)
                    Button(onClick = onDownloadModel) { Icon(Icons.Default.Download, null); Spacer(Modifier.width(6.dp)); Text("Descargar IA local") }
                }
            }
        }
        OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Pregunta…") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Button(enabled = configured && question.isNotBlank() && !busy, onClick = { onAsk(question) }, modifier = Modifier.fillMaxWidth()) {
            if (busy) { CircularProgressIndicator(modifier = Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
            Text("Preguntar offline")
        }
    }
}

@Composable
private fun AiStudio(configured: Boolean, busy: Boolean, onAsk: (String) -> Unit) {
    val options = listOf(
        Triple("Resumen de clase", Icons.Default.GraphicEq, "Haz un resumen estructurado de esta clase, con ideas principales y conceptos clave."),
        Triple("Tarjetas didácticas", Icons.Default.Style, "Crea tarjetas didácticas pregunta-respuesta basadas exclusivamente en esta clase."),
        Triple("Cuestionario", Icons.Default.Quiz, "Crea un cuestionario variado con respuestas para estudiar esta clase."),
        Triple("Preparar examen oral", Icons.Default.MenuBook, "Prepara un examen oral: organiza los temas, formula preguntas progresivas y da puntos clave para responder."),
        Triple("Informe de estudio", Icons.Default.Description, "Genera un informe de estudio organizado por temas, puntos débiles y prioridades."),
        Triple("Mapa mental", Icons.Default.AutoAwesome, "Genera la jerarquía textual de un mapa mental de esta clase.")
    )
    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Estudio", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text("Material generado localmente a partir de tus propias fuentes.", color = NotCanGray)
        }
        items(options) { (title, icon, prompt) ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable(enabled = configured && !busy) { onAsk(prompt) },
                colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = NotCanBlue)
                    Spacer(Modifier.width(12.dp))
                    Text(title, color = if (configured) NotCanOffWhite else NotCanGray, fontWeight = FontWeight.Medium)
                }
            }
        }
        if (!configured) item { Text("Descarga NotCan AI para activar estas herramientas. No requiere cuenta ni API.", color = NotCanGray) }
    }
}
