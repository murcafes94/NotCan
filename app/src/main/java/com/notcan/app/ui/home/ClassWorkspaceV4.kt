package com.notcan.app.ui.home

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.notcan.app.data.local.AudioRecordingEntity
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.DetectedCueEntity
import com.notcan.app.data.local.DocumentResourceEntity
import com.notcan.app.data.local.ImportantMomentEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.PdfInkStrokeEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.TranscriptEntity
import com.notcan.app.localai.WhisperModelState
import com.notcan.app.recording.RecordingState
import com.notcan.app.ui.theme.NotCanBlue

/** Compatibility wrapper kept while older PDF entities remain in the database. */
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
    val context = LocalContext.current
    val latestTranscript = transcripts.firstOrNull()

    Box(modifier = modifier) {
        NotCanClassWorkspaceV5(
            modifier = Modifier.matchParentSize(),
            cycleName = cycleName,
            subject = subject,
            classSession = classSession,
            audioRecordings = audioRecordings,
            importantMoments = importantMoments,
            notePages = notePages,
            selectedNoteId = selectedNoteId,
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
            onTranscribeLocal = onTranscribeLocal,
            onStartRecording = onStartRecording,
            onPauseRecording = onPauseRecording,
            onResumeRecording = onResumeRecording,
            onStopRecording = onStopRecording,
            onMarkMoment = onMarkMoment
        )

        if (latestTranscript != null && recordingState !is RecordingState.Recording && recordingState !is RecordingState.Paused) {
            IconButton(
                onClick = {
                    val text = buildString {
                        appendLine(classSession?.title ?: "Transcripción NotCan")
                        appendLine()
                        append(latestTranscript.body)
                    }
                    val intent = Intent(Intent.ACTION_SEND)
                        .setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, classSession?.title ?: "Transcripción NotCan")
                        .putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(intent, "Compartir transcripción"))
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = "Compartir transcripción", tint = NotCanBlue)
            }
        }
    }
}
