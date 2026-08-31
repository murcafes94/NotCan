package com.notcan.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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
import com.notcan.app.ui.theme.NotCanRed
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
    onDeleteClass: (String) -> Unit,
    onCreateNote: (String) -> Unit,
    onUpdateNote: (String, String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onImportNote: (String) -> Unit,
    onShareNote: (NotePageEntity) -> Unit,
    onShareAudio: (AudioRecordingEntity) -> Unit,
    onDeleteAudio: (String) -> Unit,
    onDeleteTranscript: (String) -> Unit = {},
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
    var level by remember(selectedSubjectId) {
        mutableStateOf(if (selectedSubjectId == null) HomeLevel.SUBJECTS else HomeLevel.CLASSES)
    }

    val selectedCycle = cycles.firstOrNull { it.id == selectedCycleId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
    val selectedClass = classes.firstOrNull { it.id == selectedClassId }

    BackHandler(enabled = level != HomeLevel.SUBJECTS) {
        level = when (level) {
            HomeLevel.WORKSPACE -> HomeLevel.CLASSES
            HomeLevel.CLASSES -> HomeLevel.SUBJECTS
            HomeLevel.SUBJECTS -> HomeLevel.SUBJECTS
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                level == HomeLevel.SUBJECTS || selectedSubject == null -> {
                    SubjectLanding(
                        cycles = cycles,
                        subjects = subjects,
                        selectedCycleId = selectedCycleId,
                        onSelectCycle = onSelectCycle,
                        onSelectSubject = { id ->
                            onSelectSubject(id)
                            level = HomeLevel.CLASSES
                        },
                        onAddCycle = { createDialog = CreateDialog.Cycle },
                        onAddSubject = { createDialog = CreateDialog.Subject }
                    )
                }

                level == HomeLevel.CLASSES || selectedClass == null -> {
                    SubjectClassesLanding(
                        cycleName = selectedCycle?.name,
                        subject = selectedSubject,
                        classes = classes,
                        onBack = { level = HomeLevel.SUBJECTS },
                        onSelectClass = { id ->
                            onSelectClass(id)
                            level = HomeLevel.WORKSPACE
                        },
                        onAddClass = { createDialog = CreateDialog.Class },
                        onRecordNewClass = { onStartRecording(NEW_CLASS_RECORDING_SENTINEL) },
                        onDeleteClass = onDeleteClass
                    )
                }

                else -> {
                    CompactWorkspaceHeader(
                        classTitle = selectedClass.title,
                        onBackToClasses = { level = HomeLevel.CLASSES },
                        onAddClass = { createDialog = CreateDialog.Class }
                    )

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
                        onDeleteNote = onDeleteNote,
                        onImportNote = onImportNote,
                        onShareNote = onShareNote,
                        onShareAudio = onShareAudio,
                        onDeleteAudio = onDeleteAudio,
                        onDeleteTranscript = onDeleteTranscript,
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
    }

    createDialog?.let { dialog ->
        val enabled = when (dialog) {
            CreateDialog.Cycle -> true
            CreateDialog.Subject -> selectedCycleId != null
            CreateDialog.Class -> selectedSubjectId != null
        }
        NameEntryDialog(
            title = when (dialog) {
                CreateDialog.Cycle -> "Nuevo ciclo"
                CreateDialog.Subject -> "Nueva materia"
                CreateDialog.Class -> "Nueva clase"
            },
            label = when (dialog) {
                CreateDialog.Cycle -> "Ej. 2026 · Segundo semestre"
                CreateDialog.Subject -> "Nombre de la materia"
                CreateDialog.Class -> "Opcional · se numerará automáticamente"
            },
            enabled = enabled,
            onDismiss = { createDialog = null },
            onConfirm = { name ->
                when (dialog) {
                    CreateDialog.Cycle -> onCreateCycle(name)
                    CreateDialog.Subject -> onCreateSubject(name)
                    CreateDialog.Class -> onCreateClass(name)
                }
                createDialog = null
                if (dialog == CreateDialog.Class) level = HomeLevel.CLASSES
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
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Materias", color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            IconButton(onClick = onAddSubject, enabled = selectedCycleId != null) {
                Icon(Icons.Default.Add, "Nueva materia", tint = NotCanBlue)
            }
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
                    Text("Crea la primera materia de este ciclo.", color = NotCanGray)
                    Button(onClick = onAddSubject, enabled = selectedCycleId != null) { Text("Crear materia") }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 210.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(subjects, key = { it.id }) { subject ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectSubject(subject.id) },
                        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                            Surface(color = NotCanBlue.copy(alpha = 0.16f), shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Default.School, null, tint = NotCanBlue, modifier = Modifier.padding(12.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(subject.name, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Icon(Icons.Default.ChevronRight, null, tint = NotCanGray)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectClassesLanding(
    cycleName: String?,
    subject: SubjectEntity,
    classes: List<ClassSessionEntity>,
    onBack: () -> Unit,
    onSelectClass: (String) -> Unit,
    onAddClass: () -> Unit,
    onRecordNewClass: () -> Unit,
    onDeleteClass: (String) -> Unit
) {
    var pendingDelete by remember(subject.id) { mutableStateOf<ClassSessionEntity?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Volver a materias", tint = NotCanOffWhite) }
            Column(Modifier.weight(1f)) {
                Text(subject.name, color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                cycleName?.let { Text(it, color = NotCanGray, style = MaterialTheme.typography.labelMedium) }
            }
            IconButton(onClick = onRecordNewClass) {
                Icon(Icons.Default.RadioButtonChecked, "Grabar nueva clase", tint = NotCanRed)
            }
            Button(onClick = onAddClass) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.padding(horizontal = 3.dp))
                Text("Clase")
            }
        }

        if (classes.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aún no hay clases", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text("Cuando crees o grabes la primera clase aparecerá aquí directamente.", color = NotCanGray)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onAddClass) { Text("Crear primera clase") }
                        OutlinedButton(onClick = onRecordNewClass) {
                            Icon(Icons.Default.RadioButtonChecked, null, tint = NotCanRed)
                            Spacer(Modifier.padding(horizontal = 3.dp))
                            Text("Grabar")
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 230.dp),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(classes, key = { it.id }) { classSession ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onSelectClass(classSession.id) },
                        colors = CardDefaults.cardColors(containerColor = NotCanSurface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(classSession.title, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("Abrir clase", color = NotCanGray, style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = { pendingDelete = classSession }) {
                                Icon(Icons.Default.Delete, "Eliminar clase", tint = NotCanRed)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = NotCanBlue)
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar clase") },
            text = { Text("Se eliminará ${target.title} con sus audios, transcripciones, apuntes y archivos locales. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDeleteClass(target.id)
                }) { Text("Eliminar", color = NotCanRed) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun CompactWorkspaceHeader(
    classTitle: String,
    onBackToClasses: () -> Unit,
    onAddClass: () -> Unit
) {
    Surface(color = NotCanSurface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToClasses) { Icon(Icons.Default.ArrowBack, "Clases", tint = NotCanOffWhite) }
            Text(classTitle, color = NotCanOffWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
            IconButton(onClick = onAddClass) { Icon(Icons.Default.Add, "Nueva clase", tint = NotCanBlue) }
        }
    }
}

private enum class HomeLevel { SUBJECTS, CLASSES, WORKSPACE }
