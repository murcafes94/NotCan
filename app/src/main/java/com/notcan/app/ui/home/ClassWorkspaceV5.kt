package com.notcan.app.ui.home

import android.media.MediaPlayer
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.DetectedCueEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.localai.WhisperModelState
import com.notcan.app.recording.RecordingService
import com.notcan.app.recording.RecordingState
import com.notcan.app.ui.ai.StudyArtifactKind
import com.notcan.app.ui.ai.TuNotArtifactStore
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGraphite
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanRed
import com.notcan.app.ui.theme.NotCanSurface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun NotCanClassWorkspaceV5(
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
    onDeleteNote: (String) -> Unit,
    onImportNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit,
    onDeleteTranscript: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit,
    onStartRecording: (String) -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMarkMoment: () -> Unit
) {
    val recordingActive = recordingState is RecordingState.Recording || recordingState is RecordingState.Paused
    val liveTranscript by RecordingService.liveTranscript.collectAsState()
    val liveStatus by RecordingService.aiStatus.collectAsState()
    var livePanelExpanded by remember(classSession?.id) { mutableStateOf(false) }
    val overlayBottomPadding = if (LocalConfiguration.current.screenWidthDp >= 840) 88.dp else 150.dp

    LaunchedEffect(recordingActive, classSession?.id, notePages.size) {
        if (recordingActive && classSession != null && notePages.isEmpty()) onCreateNote("Apuntes de clase")
    }

    Box(modifier.fillMaxSize()) {
        if (classSession == null) {
            EmptyClassWorkspaceV5(cycleName, subject != null, Modifier.align(Alignment.Center))
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 8.dp)) {
                if (recordingActive) {
                    FocusedRecordingDesk(
                        classSessionId = classSession.id,
                        notePages = notePages,
                        selectedNoteId = selectedNoteId,
                        liveTranscript = liveTranscript,
                        liveStatus = liveStatus,
                        onSelectNote = onSelectNote,
                        onCreateNote = onCreateNote,
                        onUpdateNote = onUpdateNote,
                        onDeleteNote = onDeleteNote,
                        onShareNote = onShareNote,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    NormalClassTabs(
                        subjectName = subject?.name,
                        classTitle = classSession.title,
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
                        onDeleteNote = onDeleteNote,
                        onImportNote = onImportNote,
                        onShareNote = onShareNote,
                        onShareAudio = onShareAudio,
                        onDeleteAudio = onDeleteAudio,
                        onDeleteTranscript = onDeleteTranscript,
                        onTranscribeLocal = onTranscribeLocal,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        if (recordingActive && livePanelExpanded) {
            LiveTranscriptPanel(
                transcript = liveTranscript,
                status = liveStatus,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = overlayBottomPadding + 246.dp)
                    .fillMaxWidth(0.88f)
                    .height(170.dp)
            )
        }

        RecordingControlsV5(
            state = recordingState,
            selectedClassId = classSession?.id,
            showTranscript = livePanelExpanded,
            onToggleTranscript = { livePanelExpanded = !livePanelExpanded },
            onStart = onStartRecording,
            onPause = onPauseRecording,
            onResume = onResumeRecording,
            onStop = onStopRecording,
            onMark = onMarkMoment,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = overlayBottomPadding)
        )
    }
}

@Composable
private fun RecordingHeader(subject: String?, classTitle: String, liveStatus: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(listOfNotNull(subject, classTitle).joinToString(" · "), color = NotCanGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
        Surface(color = NotCanRed.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Circle, null, tint = NotCanRed, modifier = Modifier.size(10.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (liveStatus.contains("paus", ignoreCase = true)) "Pausada" else "Grabando", color = NotCanOffWhite, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FocusedRecordingDesk(
    classSessionId: String,
    notePages: List<NotePageEntity>,
    selectedNoteId: String?,
    liveTranscript: String,
    liveStatus: String,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedNote = notePages.firstOrNull { it.id == selectedNoteId } ?: notePages.firstOrNull()
    val wide = LocalConfiguration.current.screenWidthDp >= 700
    var showPages by remember(classSessionId) { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = { showPages = !showPages }) {
                Icon(Icons.Default.Menu, if (showPages) "Ocultar páginas" else "Mostrar páginas", tint = NotCanBlue)
            }
        }
        if (showPages) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                items(notePages, key = { it.id }) { note ->
                    Surface(
                        color = if (note.id == selectedNote?.id) NotCanBlue.copy(alpha = 0.18f) else NotCanGraphite,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { onSelectNote(note.id); showPages = false }
                    ) {
                        Text(note.title.ifBlank { "Apuntes" }, color = if (note.id == selectedNote?.id) NotCanOffWhite else NotCanGray, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), maxLines = 1)
                    }
                }
                item { OutlinedButton(onClick = { onCreateNote("Nueva página") }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(4.dp)); Text("Página") } }
            }
            Spacer(Modifier.height(6.dp))
        }

        if (selectedNote == null) {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth().weight(1f)) {
                Column(Modifier.fillMaxSize().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Preparando tus apuntes…", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text("NotCan crea una página local para esta clase sin detener la grabación.", color = NotCanGray)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onCreateNote("Apuntes de clase") }) { Text("Crear ahora") }
                }
            }
        } else {
            WriterNoteEditor(
                selectedNote,
                onUpdateNote,
                { onShareNote(selectedNote) },
                { onDeleteNote(selectedNote.id) },
                Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun LiveTranscriptPanel(transcript: String, status: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val paused = status.contains("paus", ignoreCase = true)
    LaunchedEffect(transcript.length, paused) {
        if (!paused && scroll.maxValue > 0) scroll.animateScrollTo(scroll.maxValue)
    }

    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = NotCanGraphite), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = NotCanBlue, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(5.dp))
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Text(
                    if (transcript.isBlank()) "…" else transcript.takeLast(8000),
                    color = if (transcript.isBlank()) NotCanGray else NotCanOffWhite,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun NormalClassTabs(
    subjectName: String?,
    classTitle: String,
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
    onDeleteNote: (String) -> Unit,
    onImportNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit,
    onDeleteTranscript: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember(classSessionId) { mutableIntStateOf(0) }
    val tabs = listOf("Audio", "Transcripción", "Apuntes", "Estudio")

    Column(modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selected, containerColor = Color.Transparent, contentColor = NotCanBlue, divider = { }) {
            tabs.forEachIndexed { index, title -> Tab(selected = selected == index, onClick = { selected = index }, text = { Text(title) }) }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxSize()) {
            when (selected) {
                0 -> AudioContentV5(classSessionId, audioRecordings, importantMoments, onShareAudio, onDeleteAudio)
                1 -> TranscriptContentV5(audioRecordings, transcripts, detectedCues, whisperModelState, localWhisperBusy, localWhisperError, onTranscribeLocal, onDeleteTranscript)
                2 -> NotesContentV5(classSessionId, notePages, selectedNoteId, onSelectNote, onCreateNote, onUpdateNote, onDeleteNote, onImportNote, onShareNote)
                else -> StudyContentV5(subjectName, classTitle, transcripts, notePages, detectedCues)
            }
        }
    }
}

@Composable
private fun NotesContentV5(
    classSessionId: String,
    notePages: List<NotePageEntity>,
    selectedNoteId: String?,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onImportNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit
) {
    val selectedNote = notePages.firstOrNull { it.id == selectedNoteId } ?: notePages.firstOrNull()
    val wide = LocalConfiguration.current.screenWidthDp >= 650
    var showPages by remember(classSessionId) { mutableStateOf(false) }

    if (selectedNote == null) {
        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Apuntes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Text("Crea una página o importa texto/DOCX como apunte editable.", color = NotCanGray)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCreateNote("Apuntes") }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("Crear") }
                    OutlinedButton(onClick = { onImportNote(classSessionId) }) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(6.dp)); Text("Importar") }
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showPages = !showPages }) {
                Icon(Icons.Default.Menu, if (showPages) "Ocultar páginas" else "Páginas e importar", tint = NotCanBlue)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AnimatedVisibility(visible = showPages) {
                    NotePagesRail(notePages, selectedNote.id, onSelectNote, onCreateNote, onImportNote, classSessionId, Modifier.width(176.dp))
                }
                WriterNoteEditor(selectedNote, onUpdateNote, { onShareNote(selectedNote) }, { onDeleteNote(selectedNote.id) }, Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                AnimatedVisibility(visible = showPages) {
                    Column(Modifier.fillMaxWidth()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                            items(notePages, key = { it.id }) { note ->
                                Surface(color = if (note.id == selectedNote.id) NotCanBlue.copy(alpha = 0.18f) else NotCanGraphite, shape = RoundedCornerShape(9.dp), modifier = Modifier.clickable { onSelectNote(note.id); showPages = false }) {
                                    Text(note.title.ifBlank { "Apuntes" }, color = NotCanOffWhite, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), maxLines = 1)
                                }
                            }
                            item { OutlinedButton(onClick = { onCreateNote("Nueva página") }) { Text("+") } }
                            item { OutlinedButton(onClick = { onImportNote(classSessionId) }) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(4.dp)); Text("Importar") } }
                        }
                    }
                }
                WriterNoteEditor(selectedNote, onUpdateNote, { onShareNote(selectedNote) }, { onDeleteNote(selectedNote.id) }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NotePagesRail(
    notePages: List<NotePageEntity>,
    selectedNoteId: String,
    onSelectNote: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onImportNote: (String) -> Unit,
    classSessionId: String,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier, color = NotCanGraphite, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Páginas", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { onCreateNote("Nueva página") }, modifier = Modifier.size(34.dp)) { Icon(Icons.Default.Add, "Nueva página") }
            }
            OutlinedButton(onClick = { onImportNote(classSessionId) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileOpen, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Importar") }
            Spacer(Modifier.height(5.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(notePages, key = { it.id }) { note ->
                    Surface(modifier = Modifier.fillMaxWidth().clickable { onSelectNote(note.id) }, color = if (note.id == selectedNoteId) NotCanBlue.copy(alpha = 0.18f) else Color.Transparent, shape = RoundedCornerShape(9.dp)) {
                        Column(Modifier.padding(8.dp)) {
                            Text(note.title.ifBlank { "Apuntes" }, color = if (note.id == selectedNoteId) NotCanOffWhite else NotCanGray, maxLines = 2)
                            Text(formatDateTimeV5(note.updatedAtEpochMs), color = NotCanGray.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioContentV5(
    classSessionId: String,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit
) {
    val player = remember(classSessionId) { MediaPlayer() }
    var playingId by remember(classSessionId) { mutableStateOf<String?>(null) }
    var isPlaying by remember(classSessionId) { mutableStateOf(false) }
    DisposableEffect(player) { onDispose { runCatching { player.release() } } }

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
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Grabaciones de la clase", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("Reproduce, comparte o elimina cada audio.", color = NotCanGray)
                    Spacer(Modifier.height(9.dp))
                    if (audioRecordings.isEmpty()) Text("Todavía no hay grabaciones.", color = NotCanGray)
                    else audioRecordings.forEach { audio ->
                        AudioRowV5(audio, playingId == audio.id && isPlaying, { toggleAudio(audio) }, { onShareAudio(audio) }, { onDeleteAudio(audio.id) })
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanGraphite), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp)) {
                    Text("Momentos importantes", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    if (importantMoments.isEmpty()) Text("Pulsa ✴ durante la clase para guardar un instante importante.", color = NotCanGray)
                    else importantMoments.take(30).forEach { moment ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, null, tint = NotCanBlue, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(8.dp)); Text(formatDurationV5(moment.offsetMs), color = NotCanOffWhite)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioRowV5(audio: AudioRecordingEntity, playing: Boolean, onPlay: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    var confirmDelete by remember(audio.id) { mutableStateOf(false) }
    Surface(color = NotCanGraphite, shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlay) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausar" else "Reproducir", tint = NotCanBlue) }
            Column(Modifier.weight(1f)) {
                Text(File(audio.localPath).nameWithoutExtension, color = NotCanOffWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDateTimeV5(audio.createdAtEpochMs), color = NotCanGray, style = MaterialTheme.typography.labelSmall)
            }
            Text(formatDurationV5(audio.durationMs), color = NotCanGray)
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
private fun TranscriptContentV5(
    audioRecordings: List<AudioRecordingEntity>,
    transcripts: List<TranscriptEntity>,
    detectedCues: List<DetectedCueEntity>,
    modelState: WhisperModelState,
    busy: Boolean,
    error: String?,
    onTranscribeLocal: (String) -> Unit,
    onDeleteTranscript: (String) -> Unit
) {
    val latestAudio = audioRecordings.firstOrNull()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("Transcripción final", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text(
                        when (modelState) {
                            WhisperModelState.INSTALLED -> "Whisper large-v3-turbo listo. Puede continuar en segundo plano."
                            WhisperModelState.DOWNLOADING -> "Whisper se está descargando en segundo plano."
                            WhisperModelState.NOT_INSTALLED -> "Descarga Whisper desde IA → Fuentes. La transcripción provisional de clase usa Moonshine."
                        },
                        color = NotCanGray
                    )
                    Button(enabled = latestAudio != null && modelState == WhisperModelState.INSTALLED && !busy, onClick = { latestAudio?.let { onTranscribeLocal(it.id) } }) {
                        Text(if (busy) "Procesando…" else "Transcribir último audio")
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
        if (detectedCues.isNotEmpty()) {
            item { Text("Énfasis detectado", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold) }
            items(detectedCues, key = { it.id }) { cue ->
                Card(colors = CardDefaults.cardColors(containerColor = NotCanBlue.copy(alpha = 0.10f))) {
                    Column(Modifier.fillMaxWidth().padding(11.dp)) {
                        Text(cue.label, color = NotCanBlue, fontWeight = FontWeight.SemiBold)
                        Text(cue.excerpt, color = NotCanOffWhite)
                    }
                }
            }
        }
        if (transcripts.isEmpty()) item { Text("Todavía no hay transcripción guardada.", color = NotCanGray) }
        else items(transcripts, key = { it.id }) { transcript ->
            TranscriptRowV5(transcript = transcript, onDelete = { onDeleteTranscript(transcript.id) })
        }
    }
}

@Composable
private fun TranscriptRowV5(transcript: TranscriptEntity, onDelete: () -> Unit) {
    var confirmDelete by remember(transcript.id) { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = NotCanGraphite), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(transcript.modelName ?: "Transcripción", color = NotCanBlue, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Eliminar transcripción", tint = NotCanRed) }
            }
            Spacer(Modifier.height(5.dp))
            Text(transcript.body, color = NotCanOffWhite)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar transcripción") },
            text = { Text("Se eliminará solamente este texto y sus señales académicas asociadas. El audio original se conservará.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Eliminar", color = NotCanRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StudyContentV5(
    subjectName: String?,
    classTitle: String,
    transcripts: List<TranscriptEntity>,
    notes: List<NotePageEntity>,
    cues: List<DetectedCueEntity>
) {
    val context = LocalContext.current
    val store = remember(context) { TuNotArtifactStore(context.applicationContext) }
    val scope = remember(subjectName, classTitle) { "${subjectName.orEmpty()}::${classTitle}" }
    val artifacts = remember(scope) { store.load(scope) }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("Estudio", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                Text("${transcripts.size} transcripción(es) · ${notes.size} página(s) · ${cues.size} señal(es) académicas", color = NotCanGray)
                Text(
                    if (artifacts.isEmpty()) "Genera mapas, tarjetas o cuestionarios desde IA → Estudio; quedarán guardados aquí por clase." else "${artifacts.size} material(es) de TuNot guardados para esta clase.",
                    color = if (artifacts.isEmpty()) NotCanGray else NotCanBlue
                )
            }
        }
        if (artifacts.isNotEmpty()) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(artifacts, key = { it.id }) { artifact ->
                    val kind = when (artifact.kind) {
                        StudyArtifactKind.MAP -> "Mapa"
                        StudyArtifactKind.FLASHCARDS -> "Tarjetas"
                        StudyArtifactKind.QUIZ -> "Cuestionario"
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = NotCanGraphite), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(artifact.title, color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                                Text("$kind · abrir desde IA → Estudio", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyClassWorkspaceV5(cycleName: String?, hasSubject: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.School, null, tint = NotCanBlue, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(12.dp))
        Text(when { cycleName == null -> "Crea tu primer ciclo"; !hasSubject -> "Crea o selecciona una materia"; else -> "Crea o selecciona una clase" }, color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(5.dp)); Text("Audio, transcripción y apuntes quedarán vinculados dentro de cada clase.", color = NotCanGray)
    }
}

@Composable
private fun RecordingControlsV5(
    state: RecordingState,
    selectedClassId: String?,
    showTranscript: Boolean,
    onToggleTranscript: () -> Unit,
    onStart: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onMark: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = state is RecordingState.Recording || state is RecordingState.Paused
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (active) {
            RoundControlV5(
                Icons.Default.GraphicEq,
                if (showTranscript) "Ocultar texto" else "Mostrar texto",
                if (showTranscript) NotCanOffWhite else NotCanBlue,
                NotCanGraphite,
                onClick = onToggleTranscript
            )
            RoundControlV5(Icons.Default.Star, "Marcar momento importante", NotCanOffWhite, NotCanGraphite, onClick = onMark)
            when (state) {
                is RecordingState.Recording -> RoundControlV5(Icons.Default.Pause, "Pausar", NotCanOffWhite, NotCanGraphite, onClick = onPause)
                is RecordingState.Paused -> RoundControlV5(Icons.Default.PlayArrow, "Reanudar", NotCanOffWhite, NotCanGraphite, onClick = onResume)
                else -> Unit
            }
            RoundControlV5(Icons.Default.Stop, "Detener", NotCanRed, NotCanGraphite, onClick = onStop)
        } else {
            RoundControlV5(
                Icons.Default.RadioButtonChecked,
                if (selectedClassId == null) "Selecciona una clase" else "Comenzar grabación",
                if (selectedClassId == null) NotCanGray else NotCanRed,
                NotCanGraphite,
                selectedClassId != null
            ) { selectedClassId?.let(onStart) }
        }
    }
}

@Composable
private fun RoundControlV5(icon: ImageVector, contentDescription: String, tint: Color, background: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(modifier = Modifier.size(52.dp), shape = CircleShape, color = background, shadowElevation = 5.dp) {
        IconButton(onClick = onClick, enabled = enabled) { Icon(icon, contentDescription, tint = tint) }
    }
}

private fun formatDurationV5(durationMs: Long): String {
    val totalSeconds = (durationMs / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

private fun formatDateTimeV5(epochMs: Long): String = SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(epochMs))
