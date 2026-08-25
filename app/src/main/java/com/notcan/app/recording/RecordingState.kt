package com.notcan.app.recording

sealed interface RecordingState {
    data object Idle : RecordingState

    data class Recording(
        val startedAtEpochMs: Long,
        val outputPath: String,
        val classSessionId: String,
        val audioId: String
    ) : RecordingState

    data class Paused(
        val startedAtEpochMs: Long,
        val outputPath: String,
        val classSessionId: String,
        val audioId: String
    ) : RecordingState

    data class Finished(
        val outputPath: String,
        val classSessionId: String,
        val audioId: String,
        val durationMs: Long
    ) : RecordingState

    data class Error(val message: String) : RecordingState
}
