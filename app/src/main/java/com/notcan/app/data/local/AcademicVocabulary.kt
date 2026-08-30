package com.notcan.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Vocabulario académico reutilizable por el transcriptor.
 *
 * scope:
 * - BASE: términos incluidos por NotCan y nunca asociados a un ciclo.
 * - PERMANENT: términos conservados entre ciclos.
 * - PERSONAL: términos añadidos manualmente y nunca eliminados al borrar un ciclo.
 * - CYCLE: términos aprendidos de materiales del ciclo; se eliminan con ese ciclo.
 */
@Entity(
    tableName = "academic_vocabulary",
    indices = [
        Index("scope"),
        Index("cycleId"),
        Index("subjectId"),
        Index(value = ["normalizedTerm", "language", "area"])
    ]
)
data class AcademicVocabularyTermEntity(
    @PrimaryKey val id: String,
    val term: String,
    val normalizedTerm: String,
    val language: String = "es",
    val area: String = "general",
    val scope: String = SCOPE_CYCLE,
    val cycleId: String? = null,
    val subjectId: String? = null,
    val source: String = "manual",
    val weight: Float = 1f,
    val createdAtEpochMs: Long = System.currentTimeMillis()
) {
    companion object {
        const val SCOPE_BASE = "BASE"
        const val SCOPE_PERMANENT = "PERMANENT"
        const val SCOPE_PERSONAL = "PERSONAL"
        const val SCOPE_CYCLE = "CYCLE"
    }
}
