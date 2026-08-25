package com.notcan.app.recording

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val startedAtEpochMs: Long, val outputPath: String) : RecordingState
    data class Paused(val startedAtEpochMs: Long, val outputPath: String) : RecordingState
    data class Finished(val outputPath: String) : RecordingState
    data class Error(val message: String) : RecordingState
}
