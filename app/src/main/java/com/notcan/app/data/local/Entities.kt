package com.notcan.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "study_cycles")
data class StudyCycleEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isActive: Boolean,
    val createdAtEpochMs: Long,
    @ColumnInfo(defaultValue = "0") val startEpochDay: Long = 0L,
    @ColumnInfo(defaultValue = "0") val endEpochDay: Long = 0L
)

@Entity(
    tableName = "subjects",
    foreignKeys = [ForeignKey(
        entity = StudyCycleEntity::class,
        parentColumns = ["id"], childColumns = ["cycleId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("cycleId")]
)
data class SubjectEntity(
    @PrimaryKey val id: String,
    val cycleId: String,
    val name: String,
    val colorHex: String? = null,
    val createdAtEpochMs: Long
)

@Entity(
    tableName = "subject_schedules",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"], childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = StudyCycleEntity::class,
            parentColumns = ["id"], childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId"), Index("cycleId"), Index(value = ["weekdayIso", "startMinuteOfDay"])]
)
data class SubjectScheduleEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val cycleId: String,
    val weekdayIso: Int,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val reminderMinutesBefore: Int = 1440,
    val previewMinutesBefore: Int = 10,
    val autoStopMode: String = "ASK",
    val autoStopGraceMinutes: Int = 5,
    val calendarEventId: Long? = null,
    val createdAtEpochMs: Long
)

@Entity(
    tableName = "class_sessions",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class,
        parentColumns = ["id"], childColumns = ["subjectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("subjectId"), Index("scheduleId")]
)
data class ClassSessionEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val title: String,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val scheduleId: String? = null,
    val plannedStartEpochMs: Long? = null,
    val plannedEndEpochMs: Long? = null
)

@Entity(
    tableName = "audio_recordings",
    foreignKeys = [ForeignKey(
        entity = ClassSessionEntity::class,
        parentColumns = ["id"], childColumns = ["classSessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("classSessionId")]
)
data class AudioRecordingEntity(
    @PrimaryKey val id: String,
    val classSessionId: String,
    val localPath: String,
    val durationMs: Long,
    val createdAtEpochMs: Long,
    val backupState: String = "PENDING"
)

@Entity(
    tableName = "important_moments",
    foreignKeys = [ForeignKey(
        entity = ClassSessionEntity::class,
        parentColumns = ["id"], childColumns = ["classSessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("classSessionId"), Index("audioId")]
)
data class ImportantMomentEntity(
    @PrimaryKey val id: String,
    val classSessionId: String,
    val audioId: String,
    val offsetMs: Long,
    val note: String? = null,
    val createdAtEpochMs: Long
)

@Entity(
    tableName = "note_pages",
    foreignKeys = [ForeignKey(
        entity = ClassSessionEntity::class,
        parentColumns = ["id"], childColumns = ["classSessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("classSessionId")]
)
data class NotePageEntity(
    @PrimaryKey val id: String,
    val classSessionId: String,
    val title: String,
    val body: String,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)

@Entity(
    tableName = "document_resources",
    foreignKeys = [ForeignKey(
        entity = ClassSessionEntity::class,
        parentColumns = ["id"], childColumns = ["classSessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("classSessionId"), Index("documentType")]
)
data class DocumentResourceEntity(
    @PrimaryKey val id: String,
    val classSessionId: String,
    val displayName: String,
    val localPath: String,
    val mimeType: String,
    val documentType: String,
    val createdAtEpochMs: Long
)

@Entity(
    tableName = "pdf_ink_strokes",
    foreignKeys = [
        ForeignKey(
            entity = ClassSessionEntity::class,
            parentColumns = ["id"], childColumns = ["classSessionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DocumentResourceEntity::class,
            parentColumns = ["id"], childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("classSessionId"), Index("documentId"), Index(value = ["documentId", "pageIndex"])]
)
data class PdfInkStrokeEntity(
    @PrimaryKey val id: String,
    val classSessionId: String,
    val documentId: String,
    val pageIndex: Int,
    val tool: String,
    val colorArgb: Long,
    val baseWidth: Float,
    val pointsData: String,
    val createdAtEpochMs: Long
)

@Entity(
    tableName = "transcripts",
    foreignKeys = [ForeignKey(
        entity = ClassSessionEntity::class,
        parentColumns = ["id"], childColumns = ["classSessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("classSessionId"), Index("audioId")]
)
data class TranscriptEntity(
    @PrimaryKey val id: String,
    val classSessionId: String,
    val audioId: String?,
    val body: String,
    val status: String = "FINAL",
    val modelName: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
