package com.notcan.app.domain.model

import java.util.UUID

data class StudyCycle(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isActive: Boolean = true,
    val subjects: List<Subject> = emptyList()
)

data class Subject(
    val id: String = UUID.randomUUID().toString(),
    val cycleId: String,
    val name: String,
    val classes: List<ClassSession> = emptyList()
)

data class ClassSession(
    val id: String = UUID.randomUUID().toString(),
    val subjectId: String,
    val title: String,
    val startedAtEpochMs: Long,
    val resources: List<ClassResource> = emptyList(),
    val markers: List<ImportantMoment> = emptyList()
)

sealed interface ClassResource {
    val id: String
    val classSessionId: String

    data class Audio(
        override val id: String = UUID.randomUUID().toString(),
        override val classSessionId: String,
        val localUri: String,
        val durationMs: Long = 0L,
        val backupState: BackupState = BackupState.Pending
    ) : ClassResource

    data class Transcript(
        override val id: String = UUID.randomUUID().toString(),
        override val classSessionId: String,
        val text: String,
        val isFinal: Boolean,
        val source: TranscriptSource
    ) : ClassResource

    data class Note(
        override val id: String = UUID.randomUUID().toString(),
        override val classSessionId: String,
        val title: String,
        val body: String
    ) : ClassResource

    data class Document(
        override val id: String = UUID.randomUUID().toString(),
        override val classSessionId: String,
        val displayName: String,
        val localUri: String,
        val type: DocumentType
    ) : ClassResource

    data class MindMap(
        override val id: String = UUID.randomUUID().toString(),
        override val classSessionId: String,
        val title: String,
        val serializedGraph: String
    ) : ClassResource
}

data class ImportantMoment(
    val id: String = UUID.randomUUID().toString(),
    val classSessionId: String,
    val offsetMs: Long,
    val note: String? = null
)

enum class DocumentType { PDF, EPUB, DOC, DOCX, OTHER }
enum class TranscriptSource { LIVE_GEMINI, FINAL_GEMINI, IMPORTED, MANUAL }
enum class BackupState { Pending, Uploading, BackedUp, Failed }
