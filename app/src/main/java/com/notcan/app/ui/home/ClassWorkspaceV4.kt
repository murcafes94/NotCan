package com.notcan.app.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/**
 * Compatibility wrapper kept while older PDF entities remain in the database.
 * The active workspace is v0.7.2: Writer-style notes plus live transcription during recording.
 */
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
    // PDF/document callbacks remain in the signature for database compatibility, but documents are
    // opened externally and are not rendered inside the focused class workspace.
    NotCanClassWorkspaceV5(
        modifier = modifier,
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
}
