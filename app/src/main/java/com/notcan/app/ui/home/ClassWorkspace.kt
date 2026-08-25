package com.notcan.app.ui.home

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.recording.RecordingState
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGraphite
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun NotCanClassWorkspace(
    modifier: Modifier,
    cycleName: String?,
    subject: SubjectEntity?,
    classSession: ClassSessionEntity?,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    notePages: List<NotePageEntity>,
    selectedNoteId: String?,
    documents: List<DocumentResourceEntity>,
    recordingState: RecordingState,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onImportDocument: (String) -> Unit,
    onOpenDocument: (DocumentResourceEntity) -> Unit,
    onStartRecording: (String) -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMarkMoment: () -> Unit
) {
    Box(modifier.fillMaxSize()) {
        if (classSession == null) {
            EmptyWorkspace(
                cycleName = cycleName,
                hasSubject = subject != null,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Text(
                    listOfNotNull(cycleName, subject?.name).joinToString(" · "),
                    color = NotCanGray,
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    classSession.title,
                    color = NotCanOffWhite,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(18.dp))

                WorkspaceTabs(
                    classSessionId = classSession.id,
                    audioRecordings = audioRecordings,
                    importantMoments = importantMoments,
                    notePages = notePages,
                    selectedNoteId = selectedNoteId,
                    documents = documents,
                    onSelectNote = onSelectNote,
                    onCreateNote = onCreateNote,
                    onUpdateNote = onUpdateNote,
                    onImportDocument = onImportDocument,
                    onOpenDocument = onOpenDocument
                )
            }
        }

        RecordingControls(
            state = recordingState,
            selectedClassId = classSession?.id,
            onStart = onStartRecording,
            onPause = onPauseRecording,
            onResume = onResumeRecording,
            onStop = onStopRecording,
            onMark = onMarkMoment,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(22.dp)
        )
    }
}

@Composable
private fun EmptyWorkspace(
    cycleName: String?,
    hasSubject: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.School, contentDescription = null, tint = NotCanBlue, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            when {
                cycleName == null -> "Crea tu primer ciclo"
                !hasSubject -> "Crea o selecciona una materia"
                else -> "Crea o selecciona una clase"
            },
            color = NotCanOffWhite,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Audio, transcripción, apuntes y documentos quedarán interconectados dentro de cada clase.",
            color = NotCanGray,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WorkspaceTabs(
    classSessionId: String,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    notePages: List<NotePageEntity>,
    selectedNoteId: String?,
    documents: List<DocumentResourceEntity>,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onImportDocument: (String) -> Unit,
    onOpenDocument: (DocumentResourceEntity) -> Unit
) {
    var selected by remember(classSessionId) { mutableIntStateOf(0) }
    val tabs = listOf("Audio", "Transcripción", "Apuntes", "PDF", "EPUB", "DOCX", "Mapa mental")

    TabRow(
        selectedTabIndex = selected,
        containerColor = Color.Transparent,
        contentColor = NotCanBlue,
        divider = { }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selected == index,
                onClick = { selected = index },
                text = { Text(title, maxLines = 1) }
            )
        }
    }

    Spacer(Modifier.height(18.dp))

    when (selected) {
        0 -> AudioContent(classSessionId, audioRecordings, importantMoments)
        1 -> ModulePlaceholder(
            "Transcripción",
            "Aquí quedará la transcripción en vivo y la revisión final con Gemini. La grabación local seguirá siendo la fuente segura."
        )
        2 -> NotesContent(
            notePages = notePages,
            selectedNoteId = selectedNoteId,
            onSelectNote = onSelectNote,
            onCreateNote = onCreateNote,
            onUpdateNote = onUpdateNote
        )
        3 -> DocumentsContent(
            title = "PDF de la clase",
            classSessionId = classSessionId,
            documents = documents.filter { it.documentType == "PDF" },
            onImportDocument = onImportDocument,
            onOpenDocument = onOpenDocument
        )
        4 -> DocumentsContent(
            title = "EPUB de la clase",
            classSessionId = classSessionId,
            documents = documents.filter { it.documentType == "EPUB" },
            onImportDocument = onImportDocument,
            onOpenDocument = onOpenDocument
        )
        5 -> DocumentsContent(
            title = "Documentos Word",
            classSessionId = classSessionId,
            documents = documents.filter { it.documentType == "DOC" || it.documentType == "DOCX" },
            onImportDocument = onImportDocument,
            onOpenDocument = onOpenDocument
        )
        else -> ModulePlaceholder(
            "Mapa mental",
            "Los mapas generados o editados quedarán conectados a esta clase y disponibles también sin conexión."
        )
    }
}

@Composable
private fun AudioContent(
    classSessionId: String,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>
) {
    val player = remember(classSessionId) { MediaPlayer() }
    var playingId by remember(classSessionId) { mutableStateOf<String?>(null) }
    var isPlaying by remember(classSessionId) { mutableStateOf(false) }

    DisposableEffect(player) {
        onDispose {
            try {
                player.release()
            } catch (_: Throwable) {
            }
        }
    }

    fun toggleAudio(audio: AudioRecordingEntity) {
        try {
            if (playingId == audio.id) {
                if (player.isPlaying) {
                    player.pause()
                    isPlaying = false
                } else {
                    player.start()
                    isPlaying = true
                }
            } else {
                player.reset()
                player.setDataSource(audio.localPath)
                player.prepare()
                player.start()
                playingId = audio.id
                isPlaying = true
                player.setOnCompletionListener {
                    playingId = null
                    isPlaying = false
                }
            }
        } catch (_: Throwable) {
            playingId = null
            isPlaying = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NotCanSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mic, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Grabaciones de la clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("El audio original permanece guardado localmente durante el ciclo.", color = NotCanGray)
                    }
                }
                Spacer(Modifier.height(16.dp))

                if (audioRecordings.isEmpty()) {
                    Text("Todavía no hay grabaciones.", color = NotCanGray)
                } else {
                    audioRecordings.take(8).forEach { audio ->
                        AudioRow(
                            audio = audio,
                            playing = playingId == audio.id && isPlaying,
                            onPlay = { toggleAudio(audio) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = NotCanGraphite),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Momentos importantes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (importantMoments.isEmpty()) {
                    Text("Pulsa ✴ durante la clase para guardar un instante importante.", color = NotCanGray)
                } else {
                    importantMoments.take(20).forEach { moment ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = NotCanBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(formatDuration(moment.offsetMs), color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                            moment.note?.takeIf { it.isNotBlank() }?.let { note ->
                                Spacer(Modifier.width(10.dp))
                                Text(note, color = NotCanGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioRow(
    audio: AudioRecordingEntity,
    playing: Boolean,
    onPlay: () -> Unit
) {
    Surface(
        color = NotCanGraphite,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPlay) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pausar" else "Reproducir",
                    tint = NotCanBlue
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    File(audio.localPath).name,
                    color = NotCanOffWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(formatDateTime(audio.createdAtEpochMs), color = NotCanGray, style = MaterialTheme.typography.labelSmall)
            }
            Text(formatDuration(audio.durationMs), color = NotCanGray)
        }
    }
}

@Composable
private fun NotesContent(
    notePages: List<NotePageEntity>,
    selectedNoteId: String?,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit
) {
    val selectedNote = notePages.firstOrNull { it.id == selectedNoteId } ?: notePages.firstOrNull()

    if (selectedNote == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = NotCanSurface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("Apuntes offline", color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text("Crea una página. Se guardará localmente y seguirá disponible sin internet.", color = NotCanGray)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onCreateNote("Apuntes") }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Crear página")
                }
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.width(190.dp),
            color = NotCanGraphite,
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Páginas", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { onCreateNote("Nueva página") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Nueva página")
                    }
                }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(notePages, key = { it.id }) { note ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectNote(note.id) },
                            color = if (note.id == selectedNote.id) NotCanBlue.copy(alpha = 0.18f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    note.title.ifBlank { "Apuntes" },
                                    color = if (note.id == selectedNote.id) NotCanOffWhite else NotCanGray,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    formatDateTime(note.updatedAtEpochMs),
                                    color = NotCanGray.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }
            }
        }

        NoteEditor(
            note = selectedNote,
            onUpdateNote = onUpdateNote,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NoteEditor(
    note: NotePageEntity,
    onUpdateNote: (String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(TextFieldValue(note.body)) }

    LaunchedEffect(note.id, title, body.text) {
        delay(450)
        onUpdateNote(note.id, title, body.text)
    }

    Card(
        modifier = modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Título") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { body = surroundSelection(body, "**", "**") }) {
                    Text("B", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = { body = surroundSelection(body, "_", "_") }) {
                    Text("I")
                }
                TextButton(onClick = { body = insertAtCursor(body, "# ") }) {
                    Text("H1")
                }
                TextButton(onClick = { body = insertAtCursor(body, "• ") }) {
                    Text("• Lista")
                }
            }

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Escribe tus apuntes") },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = 10
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Guardado automático local",
                color = NotCanGray,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun surroundSelection(value: TextFieldValue, before: String, after: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val selected = value.text.substring(start, end)
    val replacement = before + selected + after
    val newText = value.text.replaceRange(start, end, replacement)
    val newCursor = if (selected.isEmpty()) start + before.length else start + replacement.length
    return value.copy(text = newText, selection = TextRange(newCursor))
}

private fun insertAtCursor(value: TextFieldValue, text: String): TextFieldValue {
    val start = value.selection.min
    val end = value.selection.max
    val newText = value.text.replaceRange(start, end, text)
    return value.copy(text = newText, selection = TextRange(start + text.length))
}

@Composable
private fun DocumentsContent(
    title: String,
    classSessionId: String,
    documents: List<DocumentResourceEntity>,
    onImportDocument: (String) -> Unit,
    onOpenDocument: (DocumentResourceEntity) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(title, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("La copia se guarda dentro de NotCan para uso offline.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { onImportDocument(classSessionId) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Importar")
                }
            }

            Spacer(Modifier.height(16.dp))

            if (documents.isEmpty()) {
                Text("No hay documentos de este tipo en la clase.", color = NotCanGray)
            } else {
                documents.forEach { document ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenDocument(document) },
                        color = NotCanGraphite,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = NotCanBlue)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    document.displayName,
                                    color = NotCanOffWhite,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${document.documentType} · ${fileSizeLabel(document.localPath)} · local",
                                    color = NotCanGray,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                            Text("Abrir", color = NotCanBlue, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ModulePlaceholder(title: String, body: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(body, color = NotCanGray)
        }
    }
}

@Composable
private fun RecordingControls(
    state: RecordingState,
    selectedClassId: String?,
    onStart: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMark: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val active = state is RecordingState.Recording || state is RecordingState.Paused

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        if (active) {
            RoundControl(
                icon = Icons.Default.Star,
                contentDescription = "Marcar momento importante",
                tint = NotCanOffWhite,
                background = NotCanBlue,
                onClick = onMark
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(visible = active && expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (state) {
                        is RecordingState.Recording -> RoundControl(
                            icon = Icons.Default.Pause,
                            contentDescription = "Pausar grabación",
                            tint = NotCanOffWhite,
                            background = NotCanSurface,
                            onClick = onPause
                        )
                        is RecordingState.Paused -> RoundControl(
                            icon = Icons.Default.PlayArrow,
                            contentDescription = "Reanudar grabación",
                            tint = NotCanOffWhite,
                            background = NotCanSurface,
                            onClick = onResume
                        )
                        else -> Unit
                    }
                    RoundControl(
                        icon = Icons.Default.Stop,
                        contentDescription = "Detener grabación",
                        tint = NotCanOffWhite,
                        background = NotCanSurface,
                        onClick = onStop
                    )
                }
            }

            if (!active) {
                RoundControl(
                    icon = Icons.Default.RadioButtonChecked,
                    contentDescription = if (selectedClassId == null) "Selecciona una clase para grabar" else "Comenzar grabación",
                    tint = if (selectedClassId == null) NotCanGray else NotCanRed,
                    background = NotCanGraphite,
                    enabled = selectedClassId != null,
                    onClick = { selectedClassId?.let(onStart) }
                )
            } else {
                RoundControl(
                    icon = Icons.Default.Circle,
                    contentDescription = "Controles de grabación",
                    tint = NotCanRed,
                    background = NotCanGraphite,
                    onClick = { expanded = !expanded }
                )
            }
        }
    }
}

@Composable
private fun RoundControl(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    background: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        color = background,
        shadowElevation = 5.dp
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = contentDescription, tint = tint)
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

private fun formatDateTime(epochMs: Long): String =
    SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(epochMs))

private fun fileSizeLabel(path: String): String {
    val bytes = File(path).length().coerceAtLeast(0L)
    return when {
        bytes >= 1_048_576L -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1_048_576.0)
        bytes >= 1_024L -> String.format(Locale.getDefault(), "%.0f KB", bytes / 1_024.0)
        else -> "$bytes B"
    }
}
