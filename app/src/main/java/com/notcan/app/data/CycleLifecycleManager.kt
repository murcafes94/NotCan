package com.notcan.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.sources.ClassSourceStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Elimina un ciclo lectivo y todo el contenido que pertenece exclusivamente a él.
 * El vocabulario BASE/PERMANENT/PERSONAL no está ligado por FK al ciclo y se conserva.
 */
class CycleLifecycleManager(context: Context) {
    private val app = context.applicationContext
    private val repository = StudyRepository(NotCanDatabase.getInstance(app).dao())
    private val sourceStore = ClassSourceStore(app)

    data class CleanupResult(
        val filesFound: Int,
        val filesDeleted: Int,
        val sourceScopesDeleted: Int,
        val calendarEventsFound: Int,
        val calendarEventsDeleted: Int
    )

    suspend fun deleteCycleCompletely(cycleId: String): CleanupResult = withContext(Dispatchers.IO) {
        val subjects = repository.cycleSubjects(cycleId)
        val classes = repository.cycleClasses(cycleId)
        val subjectsById = subjects.associateBy { it.id }
        val paths = repository.cycleFilePaths(cycleId)
        val eventIds = repository.cycleCalendarEventIds(cycleId)

        var deletedFiles = 0
        paths.forEach { path ->
            val file = File(path)
            if (!file.exists() || file.delete()) deletedFiles++
            pruneEmptyParents(file.parentFile)
        }

        // Las fuentes de TuNot usan un almacén independiente de Room, por eso se limpian aparte.
        var deletedScopes = 0
        classes.forEach { classSession ->
            val subjectName = subjectsById[classSession.subjectId]?.name
            val key = sourceStore.scopeKey(subjectName, classSession.title)
            if (sourceStore.deleteScope(key)) deletedScopes++
        }

        var deletedEvents = 0
        val canWriteCalendar = ContextCompat.checkSelfPermission(app, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        if (canWriteCalendar) {
            eventIds.forEach { eventId ->
                val deleted = runCatching {
                    app.contentResolver.delete(
                        CalendarContract.Events.CONTENT_URI,
                        "${CalendarContract.Events._ID}=?",
                        arrayOf(eventId.toString())
                    )
                }.getOrDefault(0)
                if (deleted > 0) deletedEvents++
            }
        }

        // Solo después de intentar borrar los archivos físicos eliminamos los registros.
        // Las FK CASCADE limpian todo el árbol académico del ciclo.
        repository.deleteCycleData(cycleId)

        CleanupResult(
            filesFound = paths.size,
            filesDeleted = deletedFiles,
            sourceScopesDeleted = deletedScopes,
            calendarEventsFound = eventIds.size,
            calendarEventsDeleted = deletedEvents
        )
    }

    private fun pruneEmptyParents(start: File?) {
        val filesRoot = app.filesDir.canonicalFile
        var current = start
        while (current != null) {
            val canonical = runCatching { current.canonicalFile }.getOrNull() ?: return
            if (canonical == filesRoot || !canonical.path.startsWith(filesRoot.path)) return
            if (canonical.list()?.isNotEmpty() == true) return
            if (!canonical.delete()) return
            current = canonical.parentFile
        }
    }
}
