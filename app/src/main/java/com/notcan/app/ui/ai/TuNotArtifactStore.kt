package com.notcan.app.ui.ai

import android.content.Context
import com.notcan.app.ui.maps.StudyMapArtifactParser
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal enum class StudyArtifactKind {
    MAP,
    FLASHCARDS,
    QUIZ
}

internal data class StoredStudyArtifact(
    val id: String,
    val kind: StudyArtifactKind,
    val title: String,
    val rawContent: String,
    val createdAtEpochMs: Long
)

/**
 * Biblioteca local de artefactos generados por TuNot para una materia/clase concreta.
 * Conserva el contenido crudo para poder reconstruir el mapa, las tarjetas o el cuestionario
 * sin volver a llamar al modelo.
 */
internal class TuNotArtifactStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(scope: String): List<StoredStudyArtifact> = runCatching {
        val raw = preferences.getString(key(scope), null) ?: return@runCatching emptyList()
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id")
                val kind = runCatching { StudyArtifactKind.valueOf(item.optString("kind")) }.getOrNull() ?: continue
                val title = item.optString("title")
                val content = item.optString("content")
                if (id.isBlank() || content.isBlank()) continue
                add(
                    StoredStudyArtifact(
                        id = id,
                        kind = kind,
                        title = title.ifBlank { defaultTitle(kind) },
                        rawContent = content,
                        createdAtEpochMs = item.optLong("createdAt", 0L)
                    )
                )
            }
        }.sortedByDescending { it.createdAtEpochMs }
    }.getOrDefault(emptyList())

    /** Returns the stored artifact, or null when the response is not a valid study artifact. */
    fun save(scope: String, rawContent: String): StoredStudyArtifact? {
        val descriptor = describe(rawContent) ?: return null
        val current = load(scope).toMutableList()
        current.firstOrNull { it.rawContent == rawContent }?.let { return it }

        val artifact = StoredStudyArtifact(
            id = UUID.randomUUID().toString(),
            kind = descriptor.first,
            title = descriptor.second,
            rawContent = rawContent,
            createdAtEpochMs = System.currentTimeMillis()
        )
        current.add(0, artifact)
        persist(scope, current.take(MAX_ARTIFACTS))
        return artifact
    }

    fun delete(scope: String, id: String) {
        persist(scope, load(scope).filterNot { it.id == id })
    }

    private fun describe(raw: String): Pair<StudyArtifactKind, String>? {
        StudyMapArtifactParser.parse(raw)?.let {
            return StudyArtifactKind.MAP to it.map.title.ifBlank { defaultTitle(StudyArtifactKind.MAP) }
        }
        StudyFlashcardArtifactParser.parse(raw)?.let {
            return StudyArtifactKind.FLASHCARDS to it.title.ifBlank { defaultTitle(StudyArtifactKind.FLASHCARDS) }
        }
        StudyQuizArtifactParser.parse(raw)?.let {
            return StudyArtifactKind.QUIZ to it.title.ifBlank { defaultTitle(StudyArtifactKind.QUIZ) }
        }
        return null
    }

    private fun persist(scope: String, artifacts: List<StoredStudyArtifact>) {
        val array = JSONArray()
        artifacts.take(MAX_ARTIFACTS).forEach { artifact ->
            array.put(
                JSONObject()
                    .put("id", artifact.id)
                    .put("kind", artifact.kind.name)
                    .put("title", artifact.title)
                    .put("content", artifact.rawContent)
                    .put("createdAt", artifact.createdAtEpochMs)
            )
        }
        preferences.edit().putString(key(scope), array.toString()).apply()
    }

    private fun key(scope: String): String = "artifacts_${scope.hashCode()}"

    private fun defaultTitle(kind: StudyArtifactKind): String = when (kind) {
        StudyArtifactKind.MAP -> "Mapa de estudio"
        StudyArtifactKind.FLASHCARDS -> "Tarjetas de estudio"
        StudyArtifactKind.QUIZ -> "Cuestionario"
    }

    companion object {
        private const val PREFS_NAME = "tunot_study_artifacts"
        private const val MAX_ARTIFACTS = 30
    }
}
