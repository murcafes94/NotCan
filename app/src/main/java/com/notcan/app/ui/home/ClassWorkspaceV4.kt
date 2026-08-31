package com.notcan.app.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.notcan.app.ui.theme.NotCanRed

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
    val recordingActive = recordingState is RecordingState.Recording || recordingState is RecordingState.Paused

    Box(modifier = modifier) {
        NotCanClassWorkspaceV5(
            modifier = Modifier.fillMaxSize(),
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
            onDeleteTranscript = onDeleteTranscript,
            onTranscribeLocal = onTranscribeLocal,
            onStartRecording = onStartRecording,
            onPauseRecording = onPauseRecording,
            onResumeRecording = onResumeRecording,
            onStopRecording = onStopRecording,
            onMarkMoment = onMarkMoment
        )

        if (classSession == null && subject != null && !recordingActive) {
            IconButton(
                onClick = { onStartRecording(NEW_CLASS_RECORDING_SENTINEL) },
                modifier = Modifier.align(Alignment.BottomStart).padding(18.dp)
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Crear clase y comenzar grabación", tint = NotCanRed)
            }
        }

    }
}

internal const val NEW_CLASS_RECORDING_SENTINEL = "__NOTCAN_NEW_CLASS__"
