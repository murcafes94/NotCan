package com.notcan.app.sync

import android.content.Context
import com.notcan.app.BuildConfig
import com.notcan.app.data.local.ClassSessionEntity
import com.notcan.app.data.local.GradeItemEntity
import com.notcan.app.data.local.NotePageEntity
import com.notcan.app.data.local.NotCanDao
import com.notcan.app.data.local.NotCanDatabase
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

class SupabaseSyncManager(context: Context) {
    private val app = context.applicationContext
    private val dao: NotCanDao = NotCanDatabase.getInstance(app).dao()
    private val auth = SupabaseAuthClient(app)
    private val changes = SyncChangeStore(app)

    data class Result(val pushed: Int, val pulled: Int, val message: String)

    fun isSignedIn(): Boolean = auth.currentSession() != null

    suspend fun syncNow(): Result = withContext(Dispatchers.IO) {
        val session = auth.ensureSession() ?: return@withContext Result(0, 0, "Inicia sesión para sincronizar.")
        var pushed = 0
        var pulled = 0

        val before = TABLES.associateWith { table -> fetchRows(table, session.accessToken) }
        val pendingKeys = changes.pending().mapTo(hashSetOf()) { it.entity to it.entityId }

        // Pull first for rows that are not locally pending. This prevents an old local copy
        // from overwriting a newer web copy simply because the APK was opened later.
        for (table in TABLES) {
            for (row in before.getValue(table)) {
                val id = row.optString("id")
                if ((table to id) in pendingKeys) continue
                applyRemote(table, row)
                pulled++
            }
        }

        // Upload pre-sync local records that do not exist in the account yet. This is what
        // moves data created before login into the shared account on first synchronization.
        val remoteIds = before.mapValues { (_, rows) -> rows.mapTo(hashSetOf()) { it.optString("id") } }
        for (table in TABLES) {
            for (id in localIds(table)) {
                if (id !in remoteIds.getValue(table) && (table to id) !in pendingKeys) {
                    localRow(table, id, session.userId)?.let { row ->
                        upsert(table, row, session.accessToken)
                        pushed++
                    }
                }
            }
        }

        for (change in changes.pending()) {
            if (change.entity !in TABLES) {
                changes.clear(change.entity, change.entityId)
                continue
            }
            if (change.operation == "DELETE") {
                softDelete(change.entity, change.entityId, session.accessToken)
                pushed++
                changes.clear(change.entity, change.entityId)
                continue
            }
            val row = localRow(change.entity, change.entityId, session.userId)
            if (row == null) {
                softDelete(change.entity, change.entityId, session.accessToken)
            } else {
                upsert(change.entity, row, session.accessToken)
            }
            pushed++
            changes.clear(change.entity, change.entityId)
        }

        // Final authoritative pull after pushes. Parent tables are applied first so Room FKs
        // remain valid; tombstones are applied by the same pass.
        for (table in TABLES) {
            for (row in fetchRows(table, session.accessToken)) {
                applyRemote(table, row)
                pulled++
            }
        }

        Result(pushed, pulled, "Sincronización completada: $pushed enviados · $pulled recibidos.")
    }

    private suspend fun localIds(table: String): List<String> = when (table) {
        "study_cycles" -> dao.getAllCycles().map { it.id }
        "subjects" -> dao.getAllSubjects().map { it.id }
        "class_sessions" -> dao.getAllClassSessions().map { it.id }
        "note_pages" -> dao.getAllNotePages().map { it.id }
        "grade_items" -> dao.getAllGradeItems().map { it.id }
        else -> emptyList()
    }

    private suspend fun localRow(table: String, id: String, userId: String): JSONObject? {
        val now = System.currentTimeMillis()
        val common = JSONObject()
            .put("id", id)
            .put("user_id", userId)
            .put("revision", now)
            .put("device_id", changes.deviceId())
            .put("updated_at", Instant.ofEpochMilli(now).toString())
            .put("deleted_at", JSONObject.NULL)

        return when (table) {
            "study_cycles" -> dao.getCycle(id)?.let { item ->
                common.put("name", item.name)
                    .put("is_active", item.isActive)
                    .put("start_epoch_day", item.startEpochDay)
                    .put("end_epoch_day", item.endEpochDay)
                    .put("created_at", Instant.ofEpochMilli(item.createdAtEpochMs).toString())
            }
            "subjects" -> dao.getSubject(id)?.let { item ->
                common.put("cycle_id", item.cycleId)
                    .put("name", item.name)
                    .put("color_hex", item.colorHex ?: JSONObject.NULL)
                    .put("created_at", Instant.ofEpochMilli(item.createdAtEpochMs).toString())
            }
            "class_sessions" -> dao.getClassSession(id)?.let { item ->
                common.put("subject_id", item.subjectId)
                    .put("title", item.title)
                    .put("started_at_epoch_ms", item.startedAtEpochMs)
                    .put("ended_at_epoch_ms", item.endedAtEpochMs ?: JSONObject.NULL)
                    .put("created_at", Instant.ofEpochMilli(item.createdAtEpochMs).toString())
            }
            "note_pages" -> dao.getNotePage(id)?.let { item ->
                common.put("class_session_id", item.classSessionId)
                    .put("title", item.title)
                    .put("body", item.body)
                    .put("created_at", Instant.ofEpochMilli(item.createdAtEpochMs).toString())
                    .put("updated_at", Instant.ofEpochMilli(item.updatedAtEpochMs).toString())
                    .put("revision", item.updatedAtEpochMs)
            }
            "grade_items" -> dao.getGradeItem(id)?.let { item ->
                common.put("subject_id", item.subjectId)
                    .put("title", item.title)
                    .put("score", item.score)
                    .put("max_score", item.maxScore)
                    .put("weight_percent", item.weightPercent)
                    .put("created_at", Instant.ofEpochMilli(item.createdAtEpochMs).toString())
            }
            else -> null
        }
    }

    private suspend fun applyRemote(table: String, row: JSONObject) {
        val id = row.optString("id")
        if (id.isBlank()) return
        if (!row.isNull("deleted_at")) {
            when (table) {
                "grade_items" -> dao.deleteGradeItem(id)
                "note_pages" -> dao.deleteNotePage(id)
                "class_sessions" -> dao.deleteClassSession(id)
                "subjects" -> dao.deleteSubject(id)
                "study_cycles" -> dao.deleteCycleRecord(id)
            }
            return
        }

        val createdAt = parseInstant(row.optString("created_at"))
        when (table) {
            "study_cycles" -> dao.upsertCycle(
                StudyCycleEntity(
                    id = id,
                    name = row.optString("name").ifBlank { "Ciclo" },
                    isActive = row.optBoolean("is_active", false),
                    createdAtEpochMs = createdAt,
                    startEpochDay = row.optLong("start_epoch_day", 0L),
                    endEpochDay = row.optLong("end_epoch_day", 0L)
                )
            )
            "subjects" -> {
                val cycleId = row.optString("cycle_id")
                if (dao.getCycle(cycleId) == null) return
                dao.upsertSubject(
                    SubjectEntity(
                        id = id,
                        cycleId = cycleId,
                        name = row.optString("name").ifBlank { "Materia" },
                        colorHex = row.optString("color_hex").takeIf { it.isNotBlank() },
                        createdAtEpochMs = createdAt
                    )
                )
            }
            "class_sessions" -> {
                val subjectId = row.optString("subject_id")
                if (dao.getSubject(subjectId) == null) return
                dao.upsertClassSession(
                    ClassSessionEntity(
                        id = id,
                        subjectId = subjectId,
                        title = row.optString("title").ifBlank { "Clase" },
                        startedAtEpochMs = row.optLong("started_at_epoch_ms", createdAt),
                        endedAtEpochMs = row.optLongOrNull("ended_at_epoch_ms"),
                        createdAtEpochMs = createdAt
                    )
                )
            }
            "note_pages" -> {
                val classId = row.optString("class_session_id")
                if (dao.getClassSession(classId) == null) return
                dao.upsertNotePage(
                    NotePageEntity(
                        id = id,
                        classSessionId = classId,
                        title = row.optString("title").ifBlank { "Apuntes" },
                        body = row.optString("body"),
                        createdAtEpochMs = createdAt,
                        updatedAtEpochMs = parseInstant(row.optString("updated_at")).takeIf { it > 0 } ?: createdAt
                    )
                )
            }
            "grade_items" -> {
                val subjectId = row.optString("subject_id")
                if (dao.getSubject(subjectId) == null) return
                dao.upsertGradeItem(
                    GradeItemEntity(
                        id = id,
                        subjectId = subjectId,
                        title = row.optString("title").ifBlank { "Evaluación" },
                        score = row.optDouble("score", 0.0),
                        maxScore = row.optDouble("max_score", 100.0),
                        weightPercent = row.optDouble("weight_percent", 0.0),
                        createdAtEpochMs = createdAt
                    )
                )
            }
        }
    }

    private fun fetchRows(table: String, accessToken: String): List<JSONObject> {
        val response = request("GET", "/rest/v1/$table?select=*&order=updated_at.asc", accessToken)
        val array = JSONArray(response)
        return buildList {
            for (i in 0 until array.length()) array.optJSONObject(i)?.let(::add)
        }
    }

    private fun upsert(table: String, row: JSONObject, accessToken: String) {
        request(
            "POST",
            "/rest/v1/$table?on_conflict=id",
            accessToken,
            row.toString(),
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )
    }

    private fun softDelete(table: String, id: String, accessToken: String) {
        val now = System.currentTimeMillis()
        val body = JSONObject()
            .put("deleted_at", Instant.ofEpochMilli(now).toString())
            .put("updated_at", Instant.ofEpochMilli(now).toString())
            .put("revision", now)
            .put("device_id", changes.deviceId())
        request("PATCH", "/rest/v1/$table?id=eq.$id", accessToken, body.toString())
    }

    private fun request(
        method: String,
        path: String,
        accessToken: String,
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 35_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            extraHeaders.forEach { (key, value) -> setRequestProperty(key, value) }
            if (body != null) doOutput = true
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching { JSONObject(text).optString("message") }.getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: text.take(500).ifBlank { "HTTP $code" }
                error("Supabase ($code): $message")
            }
            text
        } finally {
            connection.disconnect()
        }
    }

    private fun parseInstant(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)

    companion object {
        internal val TABLES = listOf("study_cycles", "subjects", "class_sessions", "note_pages", "grade_items")
    }
}
