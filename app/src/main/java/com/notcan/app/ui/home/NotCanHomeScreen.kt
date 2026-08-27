package com.notcan.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.DetectedCueEntity
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.PdfInkStrokeEntity
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.localai.WhisperModelState
import com.notcan.app.recording.RecordingState
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface

@Composable
fun NotCanHomeScreen(
    recordingState: RecordingState,
    cycles: List<StudyCycleEntity>,
    subjects: List<SubjectEntity>,
    classes: List<ClassSessionEntity>,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    notePages: List<NotePageEntity>,
    documents: List<DocumentResourceEntity>,
    pdfInkStrokes: List<PdfInkStrokeEntity>,
    transcripts: List<TranscriptEntity>,
    detectedCues: List<DetectedCueEntity> = emptyList(),
    whisperModelState: WhisperModelState,
    localWhisperBusy: Boolean,
    localWhisperError: String?,
    selectedCycleId: String?,
    selectedSubjectId: String?,
    selectedClassId: String?,
    selectedNoteId: String?,
    onSelectCycle: (String) -> Unit,
    onSelectSubject: (String) -> Unit,
    onSelectClass: (String) -> Unit,
    onSelectNote: (String) -> Unit,
    onCreateCycle: (String) -> Unit,
    onCreateSubject: (String) -> Unit,
    onCreateClass: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onImportNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit,
    onTranscribeLocal: (String) -> Unit,
    onImportDocument: (String) -> Unit,
    onOpenDocument: (DocumentResourceEntity) -> Unit,
    onSavePdfInkStroke: (String, Int, String, Long, Float, String) -> Unit,
    onDeletePdfInkStroke: (String) -> Unit,
    onClearPdfInkPage: (String, Int) -> Unit,
    onStartRecording: (String) -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onMarkMoment: () -> Unit
) {
    var createDialog by remember { mutableStateOf<CreateDialog?>(null) }
    var showSubjects by remember(selectedCycleId) { mutableStateOf(selectedSubjectId == null) }
    var classMenu by remember { mutableStateOf(false) }

    val selectedCycle = cycles.firstOrNull { it.id == selectedCycleId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
    val selectedClass = classes.firstOrNull { it.id == selectedClassId }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (showSubjects || selectedSubject == null) {
                SubjectLanding(
                    cycles = cycles,
                    subjects = subjects,
                    selectedCycleId = selectedCycleId,
                    onSelectCycle = onSelectCycle,
                    onSelectSubject = { id -> onSelectSubject(id); showSubjects = false },
                    onAddCycle = { createDialog = CreateDialog.Cycle },
                    onAddSubject = { createDialog = CreateDialog.Subject }
                )
            } else {
                Surface(color = NotCanSurface, tonalElevation = 2.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { showSubjects = true }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null)
                            Spacer(Modifier.padding(horizontal = 3.dp))
                            Text("Materias")
                        }
                        Column(Modifier.weight(1f)) {
                            Text(selectedSubject.name, color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                            Text(selectedCycle?.name ?: "Ciclo", color = NotCanGray, style = MaterialTheme.typography.labelSmall)
                        }
                        if (classes.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.clickable { classMenu = true },
                                color = MaterialTheme.colorScheme.background,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(selectedClass?.title ?: "Elegir clase", color = NotCanOffWhite, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, tint = NotCanGray)
                                }
                            }
                            DropdownMenu(expanded = classMenu, onDismissRequest = { classMenu = false }) {
                                classes.forEach { item ->
                                    DropdownMenuItem(text = { Text(item.title) }, onClick = { onSelectClass(item.id); classMenu = false })
                                }
                            }
                        }
                        IconButton(onClick = { createDialog = CreateDialog.Class }) { Icon(Icons.Default.Add, contentDescription = "Nueva clase", tint = NotCanBlue) }
                    }
                }

                NotCanClassWorkspaceV4(
                    modifier = Modifier.weight(1f),
                    cycleName = selectedCycle?.name,
                    subject = selectedSubject,
                    classSession = selectedClass,
                    audioRecordings = audioRecordings,
                    importantMoments = importantMoments,
                    notePages = notePages,
                    selectedNoteId = selectedNoteId,
                    documents = documents,
                    pdfInkStrokes = pdfInkStrokes,
                    transcripts = transcripts,
                    detectedCues = detectedCues,
                    recordingState = recordingState,
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
                    onTranscribeLocal = onTranscribeLocal,
                    onImportDocument = onImportDocument,
                    onOpenDocument = onOpenDocument,
                    onSavePdfInkStroke = onSavePdfInkStroke,
                    onDeletePdfInkStroke = onDeletePdfInkStroke,
                    onClearPdfInkPage = onClearPdfInkPage,
                    onStartRecording = onStartRecording,
                    onPauseRecording = onPauseRecording,
                    onResumeRecording = onResumeRecording,
                    onStopRecording = onStopRecording,
                    onMarkMoment = onMarkMoment
                )
            }
        }
    }

    createDialog?.let { dialog ->
        val enabled = when (dialog) { CreateDialog.Cycle -> true; CreateDialog.Subject -> selectedCycleId != null; CreateDialog.Class -> selectedSubjectId != null }
        NameEntryDialog(
            title = when (dialog) { CreateDialog.Cycle -> "Nuevo ciclo"; CreateDialog.Subject -> "Nueva materia"; CreateDialog.Class -> "Nueva clase" },
            label = when (dialog) { CreateDialog.Cycle -> "Ej. 2026 · Segundo semestre"; CreateDialog.Subject -> "Nombre de la materia"; CreateDialog.Class -> "Opcional · se numerará automáticamente" },
            enabled = enabled,
            onDismiss = { createDialog = null },
            onConfirm = { name ->
                when (dialog) { CreateDialog.Cycle -> onCreateCycle(name); CreateDialog.Subject -> onCreateSubject(name); CreateDialog.Class -> onCreateClass(name) }
                createDialog = null
            }
        )
    }
}

@Composable
private fun SubjectLanding(
    cycles: List<StudyCycleEntity>,
    subjects: List<SubjectEntity>,
    selectedCycleId: String?,
    onSelectCycle: (String) -> Unit,
    onSelectSubject: (String) -> Unit,
    onAddCycle: () -> Unit,
    onAddSubject: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.School, contentDescription = null, tint = NotCanBlue)
            Spacer(Modifier.padding(horizontal = 5.dp))
            Column(Modifier.weight(1f)) {
                Text("Materias", color = NotCanOffWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("Elige una materia y NotCan se centrará solo en ella.", color = NotCanGray)
            }
            IconButton(onClick = onAddSubject, enabled = selectedCycleId != null) { Icon(Icons.Default.Add, "Nueva materia", tint = NotCanBlue) }
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(cycles, key = { it.id }) { cycle ->
                OutlinedButton(onClick = { onSelectCycle(cycle.id) }, enabled = cycle.id != selectedCycleId) { Text(cycle.name) }
            }
            item { Button(onClick = onAddCycle) { Text("+ Ciclo") } }
        }

        if (subjects.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Todavía no hay materias", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("Crea la primera materia del ciclo. Después la pantalla de clase quedará limpia y centrada en esa materia.", color = NotCanGray)
                    Button(onClick = onAddSubject, enabled = selectedCycleId != null) { Text("Crear materia") }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(subjects, key = { it.id }) { subject ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectSubject(subject.id) },
                        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                        shape = RoundedCornerShape(15.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = NotCanBlue.copy(alpha = 0.18f), shape = RoundedCornerShape(10.dp)) {
                                Icon(Icons.Default.School, contentDescription = null, tint = NotCanBlue, modifier = Modifier.padding(12.dp))
                            }
                            Spacer(Modifier.padding(horizontal = 7.dp))
                            Text(subject.name, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
