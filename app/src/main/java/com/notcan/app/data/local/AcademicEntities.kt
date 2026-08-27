package com.notcan.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "grade_items",
    foreignKeys = [ForeignKey(
        entity = SubjectEntity::class,
        parentColumns = ["id"],
        childColumns = ["subjectId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("subjectId")]
)
data class GradeItemEntity(
    @PrimaryKey val id: String,
    val subjectId: String,
    val title: String,
    val score: Double,
    val maxScore: Double,
    val weightPercent: Double,
    val createdAtEpochMs: Long
) {
    val normalized: Double
        get() = if (maxScore > 0.0) (score / maxScore).coerceIn(0.0, 1.0) else 0.0

    val weightedContribution: Double
        get() = normalized * weightPercent
}

@Entity(
    tableName = "detected_cues",
    foreignKeys = [ForeignKey(
        entity = ClassSessionEntity::class,
        parentColumns = ["id"],
        childColumns = ["classSessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("classSessionId"), Index("transcriptId"), Index("label")]
)
data class DetectedCueEntity(
    @PrimaryKey val id: String,
    val classSessionId: String,
    val transcriptId: String?,
    val audioId: String?,
    val label: String,
    val keyword: String,
    val excerpt: String,
    val offsetMs: Long? = null,
    val createdAtEpochMs: Long
)
