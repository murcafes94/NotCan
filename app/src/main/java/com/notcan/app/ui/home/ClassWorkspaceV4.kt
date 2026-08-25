package com.notcan.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
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
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.PdfInkStrokeEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.recording.RecordingState
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite

@Composable
internal fun NotCanClassWorkspaceV4(
    modifier: Modifier,
    cycleName: String?,
    subject: SubjectEntity?,
    classSession: ClassSessionEntity?,
    audioRecordings: List<AudioRecordingEntity>,
    importantMoments: List<ImportantMomentEntity>,
    notePages: List<NotePageEntity>,
    selectedNoteId: String?,
    documents: List<DocumentResourceEntity>,
    pdfInkStrokes: List<PdfInkStrokeEntity>,
    recordingState: RecordingState,
    onSelectNote: (String) -> Unit,
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
    var pdfInkMode by remember(classSession?.id) { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (!pdfInkMode || classSession == null) {
            NotCanClassWorkspace(
                modifier = Modifier.fillMaxSize(),
                cycleName = cycleName,
                subject = subject,
                classSession = classSession,
                audioRecordings = audioRecordings,
                importantMoments = importantMoments,
                notePages = notePages,
                selectedNoteId = selectedNoteId,
                documents = documents,
                recordingState = recordingState,
                onSelectNote = onSelectNote,
                onCreateNote = onCreateNote,
                onUpdateNote = onUpdateNote,
                onImportDocument = onImportDocument,
                onOpenDocument = onOpenDocument,
                onStartRecording = onStartRecording,
                onPauseRecording = onPauseRecording,
                onResumeRecording = onResumeRecording,
                onStopRecording = onStopRecording,
                onMarkMoment = onMarkMoment
            )

            if (classSession != null) {
                FilledTonalButton(
                    onClick = { pdfInkMode = true },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 14.dp, end = 18.dp)
                ) {
                    Text("PDF + Pencil")
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            listOfNotNull(cycleName, subject?.name).joinToString(" · "),
                            color = NotCanGray,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            classSession.title,
                            color = NotCanOffWhite,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { pdfInkMode = false }) {
                        Text("Volver a la clase")
                    }
                }

                PdfAnnotationWorkspace(
                    documents = documents,
                    inkStrokes = pdfInkStrokes,
                    onImportDocument = { onImportDocument(classSession.id) },
                    onOpenExternally = onOpenDocument,
                    onSaveStroke = onSavePdfInkStroke,
                    onDeleteStroke = onDeletePdfInkStroke,
                    onClearPage = onClearPdfInkPage
                )
            }
        }
    }
}
