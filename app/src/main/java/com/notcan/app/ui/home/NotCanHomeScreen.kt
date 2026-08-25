package com.notcan.app.ui.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.PdfInkStrokeEntity
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.recording.RecordingState

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

    val selectedCycle = cycles.firstOrNull { it.id == selectedCycleId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }
    val selectedClass = classes.firstOrNull { it.id == selectedClassId }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val wide = maxWidth >= 900.dp

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    StudySidebar(
                        cycles = cycles,
                        selectedCycleId = selectedCycleId,
                        onSelectCycle = onSelectCycle,
                        onAddCycle = { createDialog = CreateDialog.Cycle }
                    )

                    StudyNavigator(
                        modifier = Modifier.width(292.dp),
                        subjects = subjects,
                        classes = classes,
                        selectedSubjectId = selectedSubjectId,
                        selectedClassId = selectedClassId,
                        onSelectSubject = onSelectSubject,
                        onSelectClass = onSelectClass,
                        onAddSubject = { createDialog = CreateDialog.Subject },
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
                        recordingState = recordingState,
                        onSelectNote = onSelectNote,
                        onCreateNote = onCreateNote,
                        onUpdateNote = onUpdateNote,
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
            } else {
                Row(Modifier.fillMaxSize()) {
                    CompactNavigation(
                        hasCycle = selectedCycleId != null,
                        onAddCycle = { createDialog = CreateDialog.Cycle }
                    )

                    Column(Modifier.weight(1f)) {
                        StudyNavigator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(265.dp),
                            subjects = subjects,
                            classes = classes,
                            selectedSubjectId = selectedSubjectId,
                            selectedClassId = selectedClassId,
                            onSelectSubject = onSelectSubject,
                            onSelectClass = onSelectClass,
                            onAddSubject = { createDialog = CreateDialog.Subject },
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
                            recordingState = recordingState,
                            onSelectNote = onSelectNote,
                            onCreateNote = onCreateNote,
                            onUpdateNote = onUpdateNote,
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
                CreateDialog.Class -> "Título de la clase"
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
            }
        )
    }
}
