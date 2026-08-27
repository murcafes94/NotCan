package com.notcan.app.ui.home

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.DetectedCueEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.localai.WhisperModelState
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
    transcripts: List<TranscriptEntity>,
    detectedCues: List<DetectedCueEntity> = emptyList(),
    recordingState: RecordingState,
    whisperModelState: WhisperModelState,
    localWhisperBusy: Boolean,
    localWhisperError: String?,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onImportNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit,
    onStartRecording: (String) -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMarkMoment: () -> Unit
) {
    Box(modifier.fillMaxSize()) {
        if (classSession == null) {
            EmptyWorkspace(cycleName, subject != null, Modifier.align(Alignment.Center))
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp)) {
                Text(listOfNotNull(cycleName, subject?.name).joinToString(" · "), color = NotCanGray, style = MaterialTheme.typography.labelLarge)
                Text(classSession.title, color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                WorkspaceTabs(
                    classSessionId = classSession.id,
                    audioRecordings = audioRecordings,
                    importantMoments = importantMoments,
                    notePages = notePages,
                    selectedNoteId = selectedNoteId,
                    transcripts = transcripts,
                    detectedCues = detectedCues,
                    whisperModelState = whisperModelState,
                    localWhisperBusy = localWhisperBusy,
                    localWhisperError = localWhisperError,
                    onSelectNote = onSelectNote,
                    onCreateNote = onCreateNote,
                    onUpdateNote = onUpdateNote,
                    onImportNote = onImportNote,
                    onShareNote = onShareNote,
                    onShareAudio = onShareAudio,
                    onDeleteAudio = onDeleteAudio,
                    onTranscribeLocal = onTranscribeLocal
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
            modifier = Modifier.align(Alignment.BottomEnd).padding(18.dp)
        )
    }
}

@Composable
private fun EmptyWorkspace(cycleName: String?, hasSubject: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.School, null, tint = NotCanBlue, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            when { cycleName == null -> "Crea tu primer ciclo"; !hasSubject -> "Crea o selecciona una materia"; else -> "Crea o selecciona una clase" },
            color = NotCanOffWhite,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text("Audio, transcripción y apuntes quedarán interconectados dentro de cada clase.", color = NotCanGray)
    }
}

@Composable
private fun WorkspaceTabs(
    classSessionId: String,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    notePages: List<NotePageEntity>,
    selectedNoteId: String?,
    transcripts: List<TranscriptEntity>,
    detectedCues: List<DetectedCueEntity>,
    whisperModelState: WhisperModelState,
    localWhisperBusy: Boolean,
    localWhisperError: String?,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onImportNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit
) {
    var selected by remember(classSessionId) { mutableIntStateOf(0) }
    val tabs = listOf("Audio", "Transcripción", "Apuntes", "Estudio")

    TabRow(selectedTabIndex = selected, containerColor = Color.Transparent, contentColor = NotCanBlue, divider = { }) {
        tabs.forEachIndexed { index, title -> Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) }) }
    }
    Spacer(Modifier.height(12.dp))

    when (selected) {
        0 -> AudioContent(classSessionId, audioRecordings, importantMoments, onShareAudio, onDeleteAudio)
        1 -> TranscriptContent(audioRecordings, transcripts, detectedCues, whisperModelState, localWhisperBusy, localWhisperError, onTranscribeLocal)
        2 -> NotesContent(classSessionId, notePages, selectedNoteId, onSelectNote, onCreateNote, onUpdateNote, onImportNote, onShareNote)
        else -> StudyContent(transcripts, notePages, detectedCues)
    }
}

@Composable
private fun AudioContent(
    classSessionId: String,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit
) {
    val player = remember(classSessionId) { MediaPlayer() }
    var playingId by remember(classSessionId) { mutableStateOf<String?>(null) }
    var isPlaying by remember(classSessionId) { mutableStateOf(false) }
    DisposableEffect(player) { onDispose { try { player.release() } catch (_: Throwable) { } } }

    fun toggleAudio(audio: AudioRecordingEntity) {
        try {
            if (playingId == audio.id) {
                if (player.isPlaying) { player.pause(); isPlaying = false } else { player.start(); isPlaying = true }
            } else {
                player.reset(); player.setDataSource(audio.localPath); player.prepare(); player.start()
                playingId = audio.id; isPlaying = true
                player.setOnCompletionListener { playingId = null; isPlaying = false }
            }
        } catch (_: Throwable) { playingId = null; isPlaying = false }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Mic, null, tint = NotCanBlue)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Grabaciones de la clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                            Text("Reproduce, comparte o elimina cada audio.", color = NotCanGray)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    if (audioRecordings.isEmpty()) Text("Todavía no hay grabaciones.", color = NotCanGray)
                    else audioRecordings.forEach { audio ->
                        AudioRow(audio, playingId == audio.id && isPlaying, { toggleAudio(audio) }, { onShareAudio(audio) }, { onDeleteAudio(audio.id) })
                        Spacer(Modifier.height(7.dp))
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanGraphite), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Momentos importantes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    if (importantMoments.isEmpty()) Text("Pulsa ✴ durante la clase para guardar un instante importante.", color = NotCanGray)
                    else importantMoments.take(30).forEach { moment ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = NotCanBlue, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(formatDuration(moment.offsetMs), color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioRow(audio: AudioRecordingEntity, playing: Boolean, onPlay: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember(audio.id) { mutableStateOf(false) }
    Surface(color = NotCanGraphite, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlay) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausar" else "Reproducir", tint = NotCanBlue) }
            Column(Modifier.weight(1f)) {
                Text(File(audio.localPath).nameWithoutExtension, color = NotCanOffWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDateTime(audio.createdAtEpochMs), color = NotCanGray, style = MaterialTheme.typography.labelSmall)
            }
            Text(formatDuration(audio.durationMs), color = NotCanGray)
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Compartir audio", tint = NotCanBlue) }
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar audio", tint = NotCanRed) }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar audio") },
            text = { Text("Se eliminará el M4A y sus marcadores. Las transcripciones ya creadas se conservarán como texto.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun TranscriptContent(
    audioRecordings: List<AudioRecordingEntity>,
    transcripts: List<TranscriptEntity>,
    detectedCues: List<DetectedCueEntity>,
    modelState: WhisperModelState,
    busy: Boolean,
    error: String?,
    onTranscribeLocal: (String) -> Unit
) {
    val latestAudio = audioRecordings.firstOrNull()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Transcripción local", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (modelState) {
                            WhisperModelState.INSTALLED -> "Whisper large-v3-turbo listo. La transcripción final puede continuar aunque cierres NotCan."
                            WhisperModelState.DOWNLOADING -> "El modelo de ~1,5 GB se descarga en segundo plano."
                            WhisperModelState.NOT_INSTALLED -> "Descarga primero el modelo desde IA → Fuentes."
                        }, color = NotCanGray
                    )
                    Button(
                        enabled = latestAudio != null && modelState == WhisperModelState.INSTALLED && !busy,
                        onClick = { latestAudio?.let { onTranscribeLocal(it.id) } }
                    ) { Text("Transcribir último audio en segundo plano") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }

        if (detectedCues.isNotEmpty()) {
            item { Text("Énfasis detectado", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold) }
            items(detectedCues, key = { it.id }) { cue -> CueCard(cue) }
        }

        if (transcripts.isEmpty()) item { Text("Todavía no hay transcripción guardada.", color = NotCanGray) }
        else items(transcripts, key = { it.id }) { transcript ->
            Card(colors = CardDefaults.cardColors(containerColor = NotCanGraphite), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(transcript.modelName ?: "Transcripción", color = NotCanBlue, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(7.dp))
                    Text(transcript.body, color = NotCanOffWhite)
                }
            }
        }
    }
}

@Composable
private fun CueCard(cue: DetectedCueEntity) {
    val accent = when {
        cue.label.contains("Examen") -> Color(0xFFAA73FF)
        cue.label.contains("Tarea") -> Color(0xFFFFA13B)
        cue.label.contains("Ojazos") || cue.label.contains("Importantísimo") -> Color(0xFFFF5555)
        cue.label.contains("Ojo") -> Color(0xFFFFCF4D)
        else -> NotCanBlue
    }
    Card(colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.12f)), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(cue.label, color = accent, fontWeight = FontWeight.SemiBold)
            Text(cue.excerpt, color = NotCanOffWhite)
        }
    }
}

@Composable
private fun NotesContent(
    classSessionId: String,
    notePages: List<NotePageEntity>,
    selectedNoteId: String?,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onImportNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit
) {
    val selectedNote = notePages.firstOrNull { it.id == selectedNoteId } ?: notePages.firstOrNull()
    if (selectedNote == null) {
        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Apuntes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Text("Crea una página o importa apuntes de texto que ya tengas.", color = NotCanGray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCreateNote("Apuntes") }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Crear") }
                    OutlinedButton(onClick = { onImportNote(classSessionId) }) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Importar") }
                }
            }
        }
        return
    }

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(modifier = Modifier.width(170.dp), color = NotCanGraphite, shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(9.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Páginas", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { onCreateNote("Nueva página") }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Add, "Nueva página") }
                }
                OutlinedButton(onClick = { onImportNote(classSessionId) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Importar")
                }
                Spacer(Modifier.height(5.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(notePages, key = { it.id }) { note ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onSelectNote(note.id) },
                            color = if (note.id == selectedNote.id) NotCanBlue.copy(alpha = 0.18f) else Color.Transparent,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(Modifier.padding(9.dp)) {
                                Text(note.title.ifBlank { "Apuntes" }, color = if (note.id == selectedNote.id) NotCanOffWhite else NotCanGray, maxLines = 2)
                                Text(formatDateTime(note.updatedAtEpochMs), color = NotCanGray.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        OfficeNoteEditor(selectedNote, onUpdateNote, { onShareNote(selectedNote) }, Modifier.weight(1f))
    }
}

@Composable
private fun OfficeNoteEditor(
    note: NotePageEntity,
    onUpdateNote: (String, String, String) -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier
) {
    var title by remember(note.id) { mutableStateOf(note.title) }
    val richState = rememberRichTextState()
    var loaded by remember(note.id) { mutableStateOf(false) }
    var lastSaved by remember(note.id) { mutableStateOf(note.body) }

    LaunchedEffect(note.id) {
        richState.setMarkdown(note.body)
        lastSaved = note.body
        loaded = true
    }
    LaunchedEffect(note.id, loaded) {
        if (!loaded) return@LaunchedEffect
        while (true) {
            delay(650)
            val markdown = richState.toMarkdown()
            if (markdown != lastSaved || title != note.title) {
                onUpdateNote(note.id, title, markdown)
                lastSaved = markdown
            }
        }
    }

    Card(modifier = modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Título") }, singleLine = true, modifier = Modifier.weight(1f))
                IconButton(onClick = onShare) { Icon(Icons.Default.Share, "Compartir apuntes", tint = NotCanBlue) }
            }
            Spacer(Modifier.height(7.dp))
            Divider(color = NotCanGray.copy(alpha = 0.25f))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = { richState.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }) { Icon(Icons.Default.FormatBold, "Negrita") }
                IconButton(onClick = { richState.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }) { Icon(Icons.Default.FormatItalic, "Cursiva") }
                IconButton(onClick = { richState.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) }) { Icon(Icons.Default.FormatUnderlined, "Subrayado") }
                TextButton(onClick = { richState.toggleSpanStyle(SpanStyle(fontSize = 25.sp, fontWeight = FontWeight.Bold)) }) { Text("H1") }
                TextButton(onClick = { richState.toggleSpanStyle(SpanStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold)) }) { Text("H2") }
                IconButton(onClick = { richState.toggleUnorderedList() }) { Icon(Icons.Default.FormatListBulleted, "Viñetas") }
                IconButton(onClick = { richState.toggleOrderedList() }) { Icon(Icons.Default.FormatListNumbered, "Numeración") }
                Text("  Resaltado:", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
                HighlightButton(Color(0xFFFFE066), richState = { richState.toggleSpanStyle(SpanStyle(background = Color(0xFFFFE066))) })
                HighlightButton(Color(0xFF8EE39A), richState = { richState.toggleSpanStyle(SpanStyle(background = Color(0xFF8EE39A))) })
                HighlightButton(Color(0xFF7EC8FF), richState = { richState.toggleSpanStyle(SpanStyle(background = Color(0xFF7EC8FF))) })
                HighlightButton(Color(0xFFFF9BB8), richState = { richState.toggleSpanStyle(SpanStyle(background = Color(0xFFFF9BB8))) })
                Text("  Texto:", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
                TextColorButton("A", NotCanOffWhite) { richState.toggleSpanStyle(SpanStyle(color = NotCanOffWhite)) }
                TextColorButton("A", NotCanBlue) { richState.toggleSpanStyle(SpanStyle(color = NotCanBlue)) }
                TextColorButton("A", NotCanRed) { richState.toggleSpanStyle(SpanStyle(color = NotCanRed)) }
                TextColorButton("A", Color(0xFF65C76F)) { richState.toggleSpanStyle(SpanStyle(color = Color(0xFF65C76F))) }
            }
            Divider(color = NotCanGray.copy(alpha = 0.25f))
            Spacer(Modifier.height(6.dp))
            RichTextEditor(state = richState, modifier = Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(5.dp))
            Text("Editor visual · formato directo · guardado automático local", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HighlightButton(color: Color, richState: () -> Unit) {
    Surface(modifier = Modifier.size(30.dp).clickable(onClick = richState), color = color, shape = RoundedCornerShape(6.dp)) { }
}

@Composable
private fun TextColorButton(label: String, color: Color, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(label, color = color, fontWeight = FontWeight.Bold) }
}

@Composable
private fun StudyContent(transcripts: List<TranscriptEntity>, notes: List<NotePageEntity>, cues: List<DetectedCueEntity>) {
    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Estudio", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
            Text("${transcripts.size} transcripción(es) · ${notes.size} página(s) · ${cues.size} señal(es) académicas", color = NotCanGray)
            if (cues.isNotEmpty()) {
                Text("NotCan ya está separando tareas, exámenes y frases enfatizadas para que no queden escondidas dentro de una clase larga.", color = NotCanGray)
            } else Text("Cuando transcribas, aquí aparecerán tareas, exámenes y énfasis detectados automáticamente.", color = NotCanGray)
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
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
        if (active) RoundControl(Icons.Default.Star, "Marcar momento importante", NotCanOffWhite, NotCanBlue, onClick = onMark)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AnimatedVisibility(visible = active && expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (state) {
                        is RecordingState.Recording -> RoundControl(Icons.Default.Pause, "Pausar grabación", NotCanOffWhite, NotCanSurface, onClick = onPause)
                        is RecordingState.Paused -> RoundControl(Icons.Default.PlayArrow, "Reanudar grabación", NotCanOffWhite, NotCanSurface, onClick = onResume)
                        else -> Unit
                    }
                    RoundControl(Icons.Default.Stop, "Detener grabación", NotCanOffWhite, NotCanSurface, onClick = onStop)
                }
            }
            if (!active) {
                RoundControl(Icons.Default.RadioButtonChecked, if (selectedClassId == null) "Selecciona una clase" else "Comenzar grabación", if (selectedClassId == null) NotCanGray else NotCanRed, NotCanGraphite, selectedClassId != null) { selectedClassId?.let(onStart) }
            } else RoundControl(Icons.Default.Circle, "Controles de grabación", NotCanRed, NotCanGraphite) { expanded = !expanded }
        }
    }
}

@Composable
private fun RoundControl(icon: ImageVector, contentDescription: String, tint: Color, background: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = background, shadowElevation = 5.dp) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription, tint = tint) }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun formatDateTime(epochMs: Long): String = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(epochMs))
