from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def write(path: str, content: str) -> None:
    target = ROOT / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    text = read(path)
    if old not in text:
        raise RuntimeError(f"Expected snippet not found in {path}: {old[:120]!r}")
    write(path, text.replace(old, new, 1))


# ---------------------------------------------------------------------------
# Android: robust TuNot structured-artifact parsing.
# ---------------------------------------------------------------------------
map_path = "app/src/main/java/com/notcan/app/ui/maps/StudyMapArtifactParser.kt"
map_text = read(map_path)
map_text = map_text.replace(
'''        val markerMatch = START_MARKERS
            .mapNotNull { marker -> value.indexOf(marker).takeIf { it >= 0 }?.let { Triple(it, marker.length, marker) } }
            .minByOrNull { it.first }
            ?: return null
''',
'''        val markerMatch = START_MARKERS
            .mapNotNull { marker -> value.indexOf(marker).takeIf { it >= 0 }?.let { Triple(it, marker.length, marker) } }
            .minByOrNull { it.first }
            ?: return extractBareArtifact(value)
''',
1,
)
map_text = map_text.replace(
'''    private fun balancedObjectEnd(value: String, start: Int, limit: Int): Int? {
''',
'''    private fun extractBareArtifact(value: String): MapArtifactSlice? {
        val jsonStart = value.indexOf('{').takeIf { it >= 0 } ?: return null
        val jsonEnd = balancedObjectEnd(value, jsonStart, value.length) ?: return null
        val json = value.substring(jsonStart, jsonEnd).trim()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val nodes = root.flexArray("nodes") ?: return null
        if (nodes.length() == 0) return null
        val type = root.flexString("type").lowercase()
        val edges = root.flexArray("edges")
        val looksLikeMap = type in setOf("mind_map", "concept_map", "conceptual", "concept") || edges != null
        if (!looksLikeMap) return null
        return MapArtifactSlice(json = json, start = jsonStart, endExclusive = jsonEnd)
    }

    private fun balancedObjectEnd(value: String, start: Int, limit: Int): Int? {
''',
1,
)
write(map_path, map_text)

study_path = "app/src/main/java/com/notcan/app/ui/ai/TuNotStudyArtifacts.kt"
study_text = read(study_path)
study_text = study_text.replace(
'''        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS) ?: return null
        return runCatching {
            val root = JSONObject(artifact.json)
            val title = compact(root.flexString("title").ifBlank { "Tarjetas de estudio" }, 70)
''',
'''        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("cards", "flashcards", "tarjetas") != null }
            ?: return null
        return runCatching {
            val root = JSONObject(artifact.json)
            val title = compact(root.flexString("title").ifBlank { "Tarjetas de estudio" }, 70)
''',
1,
)
study_text = study_text.replace(
'''        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS) ?: return value
        return stripStudyArtifact(value, artifact)
            .ifBlank { "Preparé las tarjetas. Puedes abrirlas y comenzar el repaso." }
''',
'''        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("cards", "flashcards", "tarjetas") != null }
            ?: return value
        return stripStudyArtifact(value, artifact)
            .ifBlank { "Preparé las tarjetas. Puedes abrirlas y comenzar el repaso." }
''',
1,
)
study_text = study_text.replace(
'''        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS) ?: return null
        return runCatching {
            val root = JSONObject(artifact.json)
            val title = compactStudyText(root.flexString("title").ifBlank { "Cuestionario" }, 80)
''',
'''        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("questions", "preguntas") != null }
            ?: return null
        return runCatching {
            val root = JSONObject(artifact.json)
            val title = compactStudyText(root.flexString("title").ifBlank { "Cuestionario" }, 80)
''',
1,
)
study_text = study_text.replace(
'''        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS) ?: return value
        return stripStudyArtifact(value, artifact)
            .ifBlank { "Preparé el cuestionario. Puedes responderlo directamente en NotCan." }
''',
'''        val artifact = extractStudyArtifact(value, START_MARKERS, END_MARKERS)
            ?: extractBareStudyArtifact(value) { root -> root.flexArray("questions", "preguntas") != null }
            ?: return value
        return stripStudyArtifact(value, artifact)
            .ifBlank { "Preparé el cuestionario. Puedes responderlo directamente en NotCan." }
''',
1,
)
study_text = study_text.replace(
'''private fun balancedJsonObjectEnd(value: String, start: Int, limit: Int): Int? {
''',
'''private fun extractBareStudyArtifact(
    value: String,
    signature: (JSONObject) -> Boolean
): StudyArtifactSlice? {
    val jsonStart = value.indexOf('{').takeIf { it >= 0 } ?: return null
    val jsonEnd = balancedJsonObjectEnd(value, jsonStart, value.length) ?: return null
    val json = value.substring(jsonStart, jsonEnd).trim()
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
    if (!signature(root)) return null
    return StudyArtifactSlice(json = json, start = jsonStart, endExclusive = jsonEnd)
}

private fun balancedJsonObjectEnd(value: String, start: Int, limit: Int): Int? {
''',
1,
)
write(study_path, study_text)

ai_screen_path = "app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt"
ai_screen_text = read(ai_screen_path)
ai_screen_text = ai_screen_text.replace(
'''        quiz != null -> StudyQuizArtifactParser.stripArtifact(raw)
        else -> raw
    }
''',
'''        quiz != null -> StudyQuizArtifactParser.stripArtifact(raw)
        else -> sanitizeUnparsedArtifact(raw)
    }
''',
1,
)
ai_screen_text = ai_screen_text.replace(
'''@Composable
private fun AiChat(
''',
'''private fun sanitizeUnparsedArtifact(raw: String): String {
    val looksStructured = raw.contains("NOTCAN_", ignoreCase = true) ||
        (raw.trimStart().startsWith("{") && (
            raw.contains("\\\"nodes\\\"") || raw.contains("\\\"cards\\\"") || raw.contains("\\\"questions\\\"")
        ))
    return if (looksStructured) {
        "TuNot generó un recurso de estudio, pero el formato llegó incompleto. Vuelve a generarlo para abrirlo de forma interactiva."
    } else raw
}

@Composable
private fun AiChat(
''',
1,
)
write(ai_screen_path, ai_screen_text)

# ---------------------------------------------------------------------------
# Android: Supabase auth + core academic sync.
# ---------------------------------------------------------------------------
write("app/src/main/java/com/notcan/app/sync/SyncChangeStore.kt", r'''package com.notcan.app.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

internal data class PendingSyncChange(
    val entity: String,
    val entityId: String,
    val operation: String,
    val changedAtEpochMs: Long
)

internal class SyncChangeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun markUpsert(entity: String, entityId: String) = put(entity, entityId, "UPSERT")
    fun markDelete(entity: String, entityId: String) = put(entity, entityId, "DELETE")

    @Synchronized
    fun pending(): List<PendingSyncChange> = runCatching {
        val array = JSONArray(prefs.getString(KEY_PENDING, "[]"))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val entity = item.optString("entity")
                val entityId = item.optString("entityId")
                val operation = item.optString("operation")
                if (entity.isBlank() || entityId.isBlank() || operation.isBlank()) continue
                add(PendingSyncChange(entity, entityId, operation, item.optLong("changedAtEpochMs")))
            }
        }.sortedBy { it.changedAtEpochMs }
    }.getOrDefault(emptyList())

    @Synchronized
    fun clear(entity: String, entityId: String) {
        val kept = pending().filterNot { it.entity == entity && it.entityId == entityId }
        save(kept)
    }

    fun deviceId(): String {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val value = "android-${UUID.randomUUID()}"
        prefs.edit().putString(KEY_DEVICE_ID, value).apply()
        return value
    }

    @Synchronized
    private fun put(entity: String, entityId: String, operation: String) {
        val current = pending().filterNot { it.entity == entity && it.entityId == entityId }.toMutableList()
        current += PendingSyncChange(entity, entityId, operation, System.currentTimeMillis())
        save(current)
    }

    private fun save(items: List<PendingSyncChange>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject()
                .put("entity", item.entity)
                .put("entityId", item.entityId)
                .put("operation", item.operation)
                .put("changedAtEpochMs", item.changedAtEpochMs))
        }
        prefs.edit().putString(KEY_PENDING, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "notcan_sync_changes"
        private const val KEY_PENDING = "pending"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
''')

write("app/src/main/java/com/notcan/app/sync/SupabaseAccountStore.kt", r'''package com.notcan.app.sync

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class SupabaseSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val expiresAtEpochSec: Long
)

internal class SupabaseAccountStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): SupabaseSession? {
        val cipherText = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val iv = prefs.getString(KEY_IV, null) ?: return null
        return runCatching {
            val json = JSONObject(decrypt(cipherText, iv))
            SupabaseSession(
                accessToken = json.getString("accessToken"),
                refreshToken = json.getString("refreshToken"),
                userId = json.getString("userId"),
                email = json.optString("email"),
                expiresAtEpochSec = json.optLong("expiresAtEpochSec")
            )
        }.getOrNull()
    }

    fun save(session: SupabaseSession) {
        val raw = JSONObject()
            .put("accessToken", session.accessToken)
            .put("refreshToken", session.refreshToken)
            .put("userId", session.userId)
            .put("email", session.email)
            .put("expiresAtEpochSec", session.expiresAtEpochSec)
            .toString()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val encrypted = cipher.doFinal(raw.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_IV).apply()
    }

    private fun decrypt(cipherText: String, ivText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(),
            GCMParameterSpec(128, Base64.decode(ivText, Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(cipherText, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            generateKey()
        }
    }

    companion object {
        private const val PREFS_NAME = "notcan_supabase_secure"
        private const val KEY_CIPHERTEXT = "session_ciphertext"
        private const val KEY_IV = "session_iv"
        private const val KEY_ALIAS = "notcan_supabase_session"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
''')

write("app/src/main/java/com/notcan/app/sync/SupabaseAuthClient.kt", r'''package com.notcan.app.sync

import android.content.Context
import com.notcan.app.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class SupabaseAuthClient(context: Context) {
    private val store = SupabaseAccountStore(context.applicationContext)

    data class SignUpResult(val session: SupabaseSession?, val confirmationRequired: Boolean)

    fun currentSession(): SupabaseSession? = store.load()

    fun signIn(email: String, password: String): SupabaseSession {
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val response = request("POST", "/auth/v1/token?grant_type=password", body)
        return parseAndSaveSession(response, email.trim())
    }

    fun signUp(email: String, password: String): SignUpResult {
        val redirect = URLEncoder.encode("https://murcafes94.github.io/NotCan/", Charsets.UTF_8.name())
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val response = request("POST", "/auth/v1/signup?redirect_to=$redirect", body)
        val access = response.optString("access_token")
        if (access.isBlank()) return SignUpResult(null, confirmationRequired = true)
        return SignUpResult(parseAndSaveSession(response, email.trim()), confirmationRequired = false)
    }

    fun ensureSession(): SupabaseSession? {
        val current = store.load() ?: return null
        val now = System.currentTimeMillis() / 1000L
        if (current.expiresAtEpochSec > now + 90L) return current
        if (current.refreshToken.isBlank()) {
            store.clear()
            return null
        }
        return runCatching {
            val body = JSONObject().put("refresh_token", current.refreshToken)
            parseAndSaveSession(
                request("POST", "/auth/v1/token?grant_type=refresh_token", body),
                current.email
            )
        }.getOrElse {
            store.clear()
            null
        }
    }

    fun signOut() {
        val session = store.load()
        if (session != null) runCatching { request("POST", "/auth/v1/logout", JSONObject(), session.accessToken) }
        store.clear()
    }

    private fun parseAndSaveSession(root: JSONObject, fallbackEmail: String): SupabaseSession {
        val access = root.optString("access_token")
        val refresh = root.optString("refresh_token")
        if (access.isBlank() || refresh.isBlank()) error("Supabase no devolvió una sesión válida.")
        val user = root.optJSONObject("user") ?: error("Supabase no devolvió el usuario.")
        val userId = user.optString("id")
        if (userId.isBlank()) error("La sesión no contiene un identificador de usuario.")
        val now = System.currentTimeMillis() / 1000L
        val expiresAt = root.optLong("expires_at").takeIf { it > now }
            ?: (now + root.optLong("expires_in", 3600L))
        val session = SupabaseSession(
            accessToken = access,
            refreshToken = refresh,
            userId = userId,
            email = user.optString("email").ifBlank { fallbackEmail },
            expiresAtEpochSec = expiresAt
        )
        store.save(session)
        return session
    }

    private fun request(method: String, path: String, body: JSONObject? = null, accessToken: String? = null): JSONObject {
        val connection = (URL(BuildConfig.SUPABASE_URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 30_000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/json")
            accessToken?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) doOutput = true
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val root = runCatching { JSONObject(text.ifBlank { "{}" }) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                val message = root.optString("msg")
                    .ifBlank { root.optString("message") }
                    .ifBlank { root.optString("error_description") }
                    .ifBlank { root.optString("error") }
                    .ifBlank { "Error de Supabase ($code)" }
                error(message)
            }
            root
        } finally {
            connection.disconnect()
        }
    }
}
''')

write("app/src/main/java/com/notcan/app/sync/SupabaseSyncManager.kt", r'''package com.notcan.app.sync

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
''')

write("app/src/main/java/com/notcan/app/ui/settings/SupabaseAccountSection.kt", r'''package com.notcan.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.notcan.app.sync.SupabaseAuthClient
import com.notcan.app.sync.SupabaseSession
import com.notcan.app.sync.SupabaseSyncManager
import com.notcan.app.ui.theme.NotCanBlue
import com.notcan.app.ui.theme.NotCanGray
import com.notcan.app.ui.theme.NotCanOffWhite
import com.notcan.app.ui.theme.NotCanSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SupabaseAccountSection() {
    val context = LocalContext.current
    val auth = remember(context) { SupabaseAuthClient(context.applicationContext) }
    val sync = remember(context) { SupabaseSyncManager(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<SupabaseSession?>(auth.currentSession()) }
    var email by remember { mutableStateOf(session?.email.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun syncAccount() {
        if (busy) return
        busy = true
        message = "Sincronizando…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { sync.syncNow() } }
            message = result.fold(
                onSuccess = { it.message },
                onFailure = { it.message ?: "No se pudo sincronizar." }
            )
            session = auth.currentSession()
            busy = false
        }
    }

    Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Icon(Icons.Default.CloudSync, contentDescription = null, tint = NotCanBlue)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Cuenta y sincronización", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                    Text(
                        session?.email?.takeIf { it.isNotBlank() } ?: "NotCan sigue funcionando sin cuenta",
                        color = if (session != null) NotCanBlue else NotCanGray,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (session == null) {
                Text(
                    "Inicia sesión con la misma cuenta de la web para compartir ciclos, materias, clases, apuntes y calificaciones. Tus audios y documentos pesados siguen locales por ahora.",
                    color = NotCanGray,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; message = null },
                    label = { Text("Correo") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; message = null },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy && email.isNotBlank() && password.length >= 6,
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { auth.signIn(email, password) }
                                }
                                result.onSuccess {
                                    session = it
                                    password = ""
                                    message = "Sesión iniciada. Sincronizando tus datos…"
                                    val synced = withContext(Dispatchers.IO) { runCatching { sync.syncNow() } }
                                    message = synced.fold(
                                        onSuccess = { value -> value.message },
                                        onFailure = { error -> error.message ?: "Sesión iniciada; la sincronización quedó pendiente." }
                                    )
                                }.onFailure { error ->
                                    message = error.message ?: "No se pudo iniciar sesión."
                                }
                                busy = false
                            }
                        }
                    ) { Text("Iniciar sesión") }
                    OutlinedButton(
                        enabled = !busy && email.isNotBlank() && password.length >= 6,
                        onClick = {
                            busy = true
                            message = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { auth.signUp(email, password) }
                                }
                                result.onSuccess { signUp ->
                                    session = signUp.session
                                    password = ""
                                    message = if (signUp.confirmationRequired) {
                                        "Cuenta creada. Confirma el correo y luego inicia sesión en NotCan."
                                    } else {
                                        "Cuenta creada y sesión iniciada."
                                    }
                                    if (signUp.session != null) {
                                        val synced = withContext(Dispatchers.IO) { runCatching { sync.syncNow() } }
                                        synced.onSuccess { message = it.message }
                                    }
                                }.onFailure { error ->
                                    message = error.message ?: "No se pudo crear la cuenta."
                                }
                                busy = false
                            }
                        }
                    ) { Text("Crear cuenta") }
                }
            } else {
                Text(
                    "La sesión se guarda cifrada con Android Keystore. La sincronización respeta la cuenta mediante RLS de Supabase.",
                    color = NotCanGray,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = !busy, onClick = ::syncAccount) { Text(if (busy) "Sincronizando…" else "Sincronizar ahora") }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = {
                            busy = true
                            scope.launch {
                                withContext(Dispatchers.IO) { runCatching { auth.signOut() } }
                                session = null
                                password = ""
                                message = "Sesión cerrada. Tus datos locales permanecen en el dispositivo."
                                busy = false
                            }
                        }
                    ) { Text("Cerrar sesión") }
                }
            }
            message?.let { Text(it, color = NotCanGray, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
        }
    }
}
''')

# BuildConfig public Supabase endpoint/key. Publishable keys are designed for client apps.
build_path = "app/build.gradle.kts"
build_text = read(build_path)
build_text = build_text.replace(
'''        versionCode = 17
        versionName = "0.8.0"
''',
'''        versionCode = 18
        versionName = "0.8.1"
        buildConfigField("String", "SUPABASE_URL", "\\\"https://xpkxvhnttquvnbcfnbck.supabase.co\\\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\\\"sb_publishable_wTChkG7BwOd00sz67lOWDQ_dZ8XdYpV\\\"")
''',
1,
)
write(build_path, build_text)

# DAO: safe Room upserts plus full/core queries used by sync.
dao_path = "app/src/main/java/com/notcan/app/data/local/NotCanDao.kt"
dao_text = read(dao_path)
dao_text = dao_text.replace("import androidx.room.Transaction\n", "import androidx.room.Transaction\nimport androidx.room.Upsert\n", 1)
dao_text = dao_text.replace(
'''    @Query("SELECT * FROM subjects WHERE id = :subjectId LIMIT 1")
    suspend fun getSubject(subjectId: String): SubjectEntity?

    @Query("SELECT * FROM class_sessions WHERE id = :classId LIMIT 1")
    suspend fun getClassSession(classId: String): ClassSessionEntity?
''',
'''    @Query("SELECT * FROM subjects WHERE id = :subjectId LIMIT 1")
    suspend fun getSubject(subjectId: String): SubjectEntity?

    @Query("SELECT * FROM class_sessions WHERE id = :classId LIMIT 1")
    suspend fun getClassSession(classId: String): ClassSessionEntity?

    @Query("SELECT * FROM note_pages WHERE id = :noteId LIMIT 1")
    suspend fun getNotePage(noteId: String): NotePageEntity?

    @Query("SELECT * FROM grade_items WHERE id = :itemId LIMIT 1")
    suspend fun getGradeItem(itemId: String): GradeItemEntity?

    @Query("SELECT * FROM study_cycles")
    suspend fun getAllCycles(): List<StudyCycleEntity>
    @Query("SELECT * FROM subjects")
    suspend fun getAllSubjects(): List<SubjectEntity>
    @Query("SELECT * FROM class_sessions")
    suspend fun getAllClassSessions(): List<ClassSessionEntity>
    @Query("SELECT * FROM note_pages")
    suspend fun getAllNotePages(): List<NotePageEntity>
    @Query("SELECT * FROM grade_items")
    suspend fun getAllGradeItems(): List<GradeItemEntity>
''',
1,
)
dao_text = dao_text.replace(
'''    @Query("SELECT c.* FROM class_sessions c INNER JOIN subjects s ON c.subjectId = s.id WHERE s.cycleId = :cycleId")
    suspend fun getClassesForCycle(cycleId: String): List<ClassSessionEntity>
''',
'''    @Query("SELECT c.* FROM class_sessions c INNER JOIN subjects s ON c.subjectId = s.id WHERE s.cycleId = :cycleId")
    suspend fun getClassesForCycle(cycleId: String): List<ClassSessionEntity>

    @Query("SELECT n.* FROM note_pages n INNER JOIN class_sessions c ON n.classSessionId = c.id INNER JOIN subjects s ON c.subjectId = s.id WHERE s.cycleId = :cycleId")
    suspend fun getNotesForCycle(cycleId: String): List<NotePageEntity>

    @Query("SELECT g.* FROM grade_items g INNER JOIN subjects s ON g.subjectId = s.id WHERE s.cycleId = :cycleId")
    suspend fun getGradesForCycle(cycleId: String): List<GradeItemEntity>
''',
1,
)
dao_text = dao_text.replace(
'''    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabularyTerm(term: AcademicVocabularyTermEntity)
''',
'''    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabularyTerm(term: AcademicVocabularyTermEntity)

    @Upsert
    suspend fun upsertCycle(cycle: StudyCycleEntity)
    @Upsert
    suspend fun upsertSubject(subject: SubjectEntity)
    @Upsert
    suspend fun upsertClassSession(classSession: ClassSessionEntity)
    @Upsert
    suspend fun upsertNotePage(notePage: NotePageEntity)
    @Upsert
    suspend fun upsertGradeItem(item: GradeItemEntity)
''',
1,
)
dao_text = dao_text.replace(
'''    @Query("DELETE FROM note_pages WHERE id = :noteId")
    suspend fun deleteNotePage(noteId: String)
''',
'''    @Query("DELETE FROM note_pages WHERE id = :noteId")
    suspend fun deleteNotePage(noteId: String)
    @Query("DELETE FROM class_sessions WHERE id = :classId")
    suspend fun deleteClassSession(classId: String)
    @Query("DELETE FROM subjects WHERE id = :subjectId")
    suspend fun deleteSubject(subjectId: String)
''',
1,
)
write(dao_path, dao_text)

repo_path = "app/src/main/java/com/notcan/app/data/StudyRepository.kt"
repo_text = read(repo_path)
repo_text = repo_text.replace("package com.notcan.app.data\n\n", "package com.notcan.app.data\n\nimport android.content.Context\n", 1)
repo_text = repo_text.replace("import kotlinx.coroutines.flow.Flow\n", "import com.notcan.app.sync.SyncChangeStore\nimport kotlinx.coroutines.flow.Flow\n", 1)
repo_text = repo_text.replace(
"class StudyRepository(private val dao: NotCanDao) {\n",
'''class StudyRepository(private val dao: NotCanDao, context: Context? = null) {
    private val syncChanges = context?.applicationContext?.let { SyncChangeStore(it) }

    private fun markUpsert(entity: String, id: String) { syncChanges?.markUpsert(entity, id) }
    private fun markDelete(entity: String, id: String) { syncChanges?.markDelete(entity, id) }
''',
1,
)
repo_text = repo_text.replace(
'''        dao.insertCycle(cycle)
        return cycle
    }

    suspend fun setActiveCycle(cycleId: String) = dao.setActiveCycle(cycleId)
    suspend fun updateCycleDates(cycleId: String, startEpochDay: Long, endEpochDay: Long) =
        dao.updateCycleDates(cycleId, startEpochDay, endEpochDay)
''',
'''        dao.insertCycle(cycle)
        if (makeActive) dao.getAllCycles().forEach { markUpsert("study_cycles", it.id) }
        else markUpsert("study_cycles", cycle.id)
        return cycle
    }

    suspend fun setActiveCycle(cycleId: String) {
        dao.setActiveCycle(cycleId)
        dao.getAllCycles().forEach { markUpsert("study_cycles", it.id) }
    }
    suspend fun updateCycleDates(cycleId: String, startEpochDay: Long, endEpochDay: Long) {
        dao.updateCycleDates(cycleId, startEpochDay, endEpochDay)
        markUpsert("study_cycles", cycleId)
    }
''',
1,
)
repo_text = repo_text.replace("        dao.insertSubject(subject)\n        return subject\n", "        dao.insertSubject(subject)\n        markUpsert(\"subjects\", subject.id)\n        return subject\n", 1)
repo_text = repo_text.replace("        dao.insertClassSession(classSession)\n        return classSession\n", "        dao.insertClassSession(classSession)\n        markUpsert(\"class_sessions\", classSession.id)\n        return classSession\n", 1)
repo_text = repo_text.replace("        dao.insertClassSession(session)\n        return session\n", "        dao.insertClassSession(session)\n        markUpsert(\"class_sessions\", session.id)\n        return session\n", 1)
repo_text = repo_text.replace("        dao.insertNotePage(note)\n        return note\n", "        dao.insertNotePage(note)\n        markUpsert(\"note_pages\", note.id)\n        return note\n", 1)
repo_text = repo_text.replace("        dao.insertGradeItem(item)\n        return item\n", "        dao.insertGradeItem(item)\n        markUpsert(\"grade_items\", item.id)\n        return item\n", 1)
repo_text = repo_text.replace(
'''    suspend fun deleteGradeItem(itemId: String) = dao.deleteGradeItem(itemId)
    suspend fun saveDetectedCue(cue: DetectedCueEntity) = dao.insertDetectedCue(cue)
    suspend fun deleteDetectedCuesForTranscript(transcriptId: String) = dao.deleteDetectedCuesForTranscript(transcriptId)
    suspend fun saveNotePage(notePage: NotePageEntity) = dao.insertNotePage(notePage)
    suspend fun deleteNotePage(noteId: String) = dao.deleteNotePage(noteId)
''',
'''    suspend fun deleteGradeItem(itemId: String) {
        markDelete("grade_items", itemId)
        dao.deleteGradeItem(itemId)
    }
    suspend fun saveDetectedCue(cue: DetectedCueEntity) = dao.insertDetectedCue(cue)
    suspend fun deleteDetectedCuesForTranscript(transcriptId: String) = dao.deleteDetectedCuesForTranscript(transcriptId)
    suspend fun saveNotePage(notePage: NotePageEntity) {
        dao.insertNotePage(notePage)
        markUpsert("note_pages", notePage.id)
    }
    suspend fun deleteNotePage(noteId: String) {
        markDelete("note_pages", noteId)
        dao.deleteNotePage(noteId)
    }
''',
1,
)
repo_text = repo_text.replace(
'''    suspend fun cycleCalendarEventIds(cycleId: String): List<Long> = dao.getCalendarEventIdsForCycle(cycleId)
    suspend fun deleteCycleData(cycleId: String) = dao.deleteCycleData(cycleId)
''',
'''    suspend fun cycleCalendarEventIds(cycleId: String): List<Long> = dao.getCalendarEventIdsForCycle(cycleId)
    suspend fun deleteCycleData(cycleId: String) {
        dao.getNotesForCycle(cycleId).forEach { markDelete("note_pages", it.id) }
        dao.getGradesForCycle(cycleId).forEach { markDelete("grade_items", it.id) }
        dao.getClassesForCycle(cycleId).forEach { markDelete("class_sessions", it.id) }
        dao.getSubjectsForCycle(cycleId).forEach { markDelete("subjects", it.id) }
        markDelete("study_cycles", cycleId)
        dao.deleteCycleData(cycleId)
    }
''',
1,
)
write(repo_path, repo_text)

# Pass context to repository wherever core user mutations originate.
replace_once(
    "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt",
    "private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao())",
    "private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao(), application)"
)
vm_path = "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt"
vm_text = read(vm_path)
vm_text = vm_text.replace("import com.notcan.app.ui.home.NoteDocxImporter\n", "import com.notcan.app.ui.home.NoteDocxImporter\nimport com.notcan.app.sync.SupabaseSyncManager\n", 1)
vm_text = vm_text.replace("    private val aiService = NotCanAiService(application)\n", "    private val aiService = NotCanAiService(application)\n    private val syncManager = SupabaseSyncManager(application)\n", 1)
vm_text = vm_text.replace(
'''    init {
        viewModelScope.launch {
''',
'''    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (syncManager.isSignedIn()) runCatching { syncManager.syncNow() }
        }
        viewModelScope.launch {
''',
1,
)
write(vm_path, vm_text)
replace_once(
    "app/src/main/java/com/notcan/app/ui/AcademicExtrasViewModel.kt",
    "private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao())",
    "private val repository = StudyRepository(NotCanDatabase.getInstance(application).dao(), application)"
)
replace_once(
    "app/src/main/java/com/notcan/app/MainActivity.kt",
    "private val recordingRepository by lazy { StudyRepository(NotCanDatabase.getInstance(this).dao()) }",
    "private val recordingRepository by lazy { StudyRepository(NotCanDatabase.getInstance(this).dao(), this) }"
)
replace_once(
    "app/src/main/java/com/notcan/app/data/CycleLifecycleManager.kt",
    "private val repository = StudyRepository(NotCanDatabase.getInstance(app).dao())",
    "private val repository = StudyRepository(NotCanDatabase.getInstance(app).dao(), app)"
)

settings_path = "app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt"
settings_text = read(settings_path)
settings_text = settings_text.replace(
'''        AcademicPeriodSettings(
            cycle = activeCycle,
''',
'''        SupabaseAccountSection()

        AcademicPeriodSettings(
            cycle = activeCycle,
''',
1,
)
write(settings_path, settings_text)

# ---------------------------------------------------------------------------
# Web: replace misleading cloud-provider selector with real Mistral/free/local.
# ---------------------------------------------------------------------------
write("web/src/lib/ai.ts", r'''import { supabase } from './supabase'

export type AiProvider = 'auto' | 'local' | 'mistral' | 'free'

export type AiContextItem = {
  title: string
  body: string
  subject?: string
  classTitle?: string
}

export type AiRequest = {
  prompt: string
  context?: AiContextItem[]
  mode?: 'chat' | 'summary' | 'questions' | 'concept-map'
  provider?: AiProvider
}

export type AiResponse = {
  answer: string
  model?: string
  provider?: string
}

const PROVIDER_KEY = 'notcan-ai-provider'
const LOCAL_URL_KEY = 'notcan-ai-local-url'
const LOCAL_MODEL_KEY = 'notcan-ai-local-model'
const MISTRAL_AGENT_KEY = 'notcan-mistral-agent-id'
const MISTRAL_API_SESSION_KEY = 'notcan-mistral-api-key-session'

export function getAiProvider(): AiProvider {
  const value = localStorage.getItem(PROVIDER_KEY)
  return ['auto', 'local', 'mistral', 'free'].includes(value || '')
    ? value as AiProvider
    : 'auto'
}

export function setAiProvider(provider: AiProvider) {
  localStorage.setItem(PROVIDER_KEY, provider)
}

export function getLocalAiConfig() {
  return {
    url: localStorage.getItem(LOCAL_URL_KEY) || 'http://127.0.0.1:11434',
    model: localStorage.getItem(LOCAL_MODEL_KEY) || 'qwen3:1.7b',
  }
}

export function setLocalAiConfig(url: string, model: string) {
  localStorage.setItem(LOCAL_URL_KEY, url.trim().replace(/\/$/, ''))
  localStorage.setItem(LOCAL_MODEL_KEY, model.trim() || 'qwen3:1.7b')
}

export function getMistralConfig() {
  return {
    agentId: localStorage.getItem(MISTRAL_AGENT_KEY) || '',
    apiKey: sessionStorage.getItem(MISTRAL_API_SESSION_KEY) || '',
  }
}

export function setMistralConfig(agentId: string, apiKey?: string) {
  localStorage.setItem(MISTRAL_AGENT_KEY, agentId.trim())
  if (apiKey !== undefined) {
    const clean = apiKey.trim()
    if (clean) sessionStorage.setItem(MISTRAL_API_SESSION_KEY, clean)
    else sessionStorage.removeItem(MISTRAL_API_SESSION_KEY)
  }
}

export function clearMistralSessionKey() {
  sessionStorage.removeItem(MISTRAL_API_SESSION_KEY)
}

function buildLocalPrompt(request: AiRequest) {
  const modeInstruction = request.mode === 'summary'
    ? 'Resume el material de forma clara, fiel y útil para estudiar.'
    : request.mode === 'questions'
      ? 'Crea preguntas de estudio con sus respuestas basadas en el material disponible.'
      : request.mode === 'concept-map'
        ? 'Organiza la respuesta como mapa conceptual textual, con concepto central, nodos y relaciones.'
        : 'Responde la consulta académica con claridad y precisión.'

  const context = (request.context || []).slice(0, 8).map((item, index) =>
    `[Fuente ${index + 1}] ${item.title}\n${item.subject ? `Materia: ${item.subject}\n` : ''}${item.classTitle ? `Clase: ${item.classTitle}\n` : ''}${item.body.slice(0, 7000)}`,
  ).join('\n\n')

  return `Eres TuNot, el asistente académico de NotCan. Responde en español claro; no inventes autores, citas, páginas ni referencias; si hay material de NotCan, úsalo como contexto y distingue lo que procede de las fuentes. Conserva literalmente los textos entre comillas cuando debas citarlos.\nTarea: ${modeInstruction}\n\nConsulta del estudiante:\n${request.prompt}${context ? `\n\nMaterial disponible en NotCan:\n${context}` : ''}`
}

async function askLocal(request: AiRequest): Promise<AiResponse> {
  const { url, model } = getLocalAiConfig()
  const controller = new AbortController()
  const timeout = window.setTimeout(() => controller.abort(), 12000)

  try {
    const response = await fetch(`${url}/api/generate`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        model,
        prompt: buildLocalPrompt(request),
        stream: false,
        options: { num_predict: 2048 },
      }),
      signal: controller.signal,
    })

    if (!response.ok) throw new Error(`Ollama respondió ${response.status}`)
    const data = await response.json()
    const answer = String(data?.response || '').trim()
    if (!answer) throw new Error('La IA local no devolvió texto.')
    return { answer, model, provider: 'local' }
  } finally {
    window.clearTimeout(timeout)
  }
}

async function askCloud(request: AiRequest, provider: 'auto' | 'mistral' | 'free'): Promise<AiResponse> {
  if (!supabase) throw new Error('Supabase no está configurado.')

  const { data: sessionData } = await supabase.auth.getSession()
  if (!sessionData.session) throw new Error('Inicia sesión para usar NotCan AI en la nube.')

  const mistral = getMistralConfig()
  if (provider === 'mistral' && (!mistral.agentId || !mistral.apiKey)) {
    throw new Error('Configura el Agent ID y la API key de Mistral. La clave se conserva solo durante esta sesión del navegador.')
  }

  const { data, error } = await supabase.functions.invoke('notcan-ai', {
    body: {
      ...request,
      provider,
      mistralAgentId: mistral.agentId || undefined,
      mistralApiKey: mistral.apiKey || undefined,
    },
  })

  if (error) throw new Error(error.message || 'No se pudo conectar con NotCan AI.')
  if (!data?.answer) throw new Error(data?.error || 'NotCan AI no devolvió una respuesta.')

  return {
    answer: String(data.answer),
    model: data.model ? String(data.model) : undefined,
    provider: data.provider ? String(data.provider) : provider,
  }
}

export async function askNotCanAi(request: AiRequest): Promise<AiResponse> {
  const selected = request.provider || getAiProvider()

  if (selected === 'local') {
    try {
      return await askLocal(request)
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error)
      throw new Error(`No pude conectar con la IA local. Comprueba que Ollama esté encendido y que la URL/modelo sean correctos. ${message}`)
    }
  }

  if (selected === 'auto') {
    if (localStorage.getItem(LOCAL_URL_KEY)) {
      try {
        return await askLocal(request)
      } catch {
        // Si el equipo local no está disponible, continuamos con Mistral (si está configurado)
        // o con el modo gratuito basado en las fuentes de NotCan.
      }
    }
    return askCloud(request, 'auto')
  }

  return askCloud(request, selected)
}
''')

write("web/src/ai-provider-ui.ts", r'''import {
  clearMistralSessionKey,
  getAiProvider,
  getLocalAiConfig,
  getMistralConfig,
  setAiProvider,
  setLocalAiConfig,
  setMistralConfig,
  type AiProvider,
} from './lib/ai'

const providerLabels: Record<AiProvider, string> = {
  auto: 'Automático',
  local: 'Local (Ollama)',
  mistral: 'Mistral · TuNot',
  free: 'Gratuito · mis fuentes',
}

function buildProviderPanel() {
  const host = document.querySelector<HTMLElement>('.ai-main-card')
  if (!host || host.querySelector('.ai-provider-panel')) return

  const panel = document.createElement('section')
  panel.className = 'ai-provider-panel'

  const heading = document.createElement('div')
  heading.className = 'ai-provider-heading'
  heading.innerHTML = '<div><strong>Motor de TuNot</strong><small>Usa Mistral, Ollama local o el modo gratuito basado en tus fuentes.</small></div>'

  const select = document.createElement('select')
  select.className = 'ai-provider-select'
  ;(['auto', 'mistral', 'local', 'free'] as AiProvider[]).forEach((provider) => {
    const option = document.createElement('option')
    option.value = provider
    option.textContent = providerLabels[provider]
    select.appendChild(option)
  })
  select.value = getAiProvider()
  heading.appendChild(select)
  panel.appendChild(heading)

  const help = document.createElement('p')
  help.className = 'ai-provider-help'
  panel.appendChild(help)

  const mistralSettings = document.createElement('div')
  mistralSettings.className = 'ai-local-settings ai-mistral-settings'
  const mistral = getMistralConfig()
  mistralSettings.innerHTML = `
    <label><span>Agent ID de Mistral</span><input class="ai-mistral-agent" value="${mistral.agentId}" placeholder="ag:..."></label>
    <label><span>API key de Mistral</span><input class="ai-mistral-key" type="password" value="" placeholder="Se guarda solo mientras esta pestaña/sesión siga abierta"></label>
    <div class="ai-provider-actions">
      <button type="button" class="secondary ai-mistral-save">Guardar para esta sesión</button>
      <button type="button" class="secondary ai-mistral-clear">Borrar clave</button>
    </div>
    <small>La API key no se guarda de forma permanente en el navegador: se envía por HTTPS a la función autenticada de NotCan únicamente para realizar la consulta.</small>
  `
  panel.appendChild(mistralSettings)

  const localSettings = document.createElement('div')
  localSettings.className = 'ai-local-settings'
  const config = getLocalAiConfig()
  localSettings.innerHTML = `
    <label><span>URL de Ollama</span><input class="ai-local-url" value="${config.url}" placeholder="http://127.0.0.1:11434"></label>
    <label><span>Modelo local</span><input class="ai-local-model" value="${config.model}" placeholder="qwen3:1.7b"></label>
    <button type="button" class="secondary ai-local-save">Guardar conexión local</button>
    <small>Para usar la IA local desde otra tablet, Ollama debe estar accesible en la misma red y permitir conexiones desde el navegador.</small>
  `
  panel.appendChild(localSettings)

  function refreshProviderUi() {
    const provider = select.value as AiProvider
    localSettings.classList.toggle('visible', provider === 'local' || provider === 'auto')
    mistralSettings.classList.toggle('visible', provider === 'mistral' || provider === 'auto')
    help.textContent = provider === 'auto'
      ? 'Automático prueba Ollama si configuraste una URL; después usa Mistral si hay credenciales en esta sesión y, como respaldo, el modo gratuito basado en tus apuntes.'
      : provider === 'local'
        ? 'Las consultas se procesan en tu propio equipo mediante Ollama.'
        : provider === 'mistral'
          ? 'Usa el mismo motor TuNot/Mistral de la APK. Debes indicar tu Agent ID y una API key para esta sesión.'
          : 'No usa una API de IA externa: extrae y organiza información de los apuntes que hayas enviado como contexto.'
  }

  select.addEventListener('change', () => {
    setAiProvider(select.value as AiProvider)
    refreshProviderUi()
  })

  localSettings.querySelector<HTMLButtonElement>('.ai-local-save')?.addEventListener('click', () => {
    const url = localSettings.querySelector<HTMLInputElement>('.ai-local-url')?.value || ''
    const model = localSettings.querySelector<HTMLInputElement>('.ai-local-model')?.value || ''
    setLocalAiConfig(url, model)
    const button = localSettings.querySelector<HTMLButtonElement>('.ai-local-save')
    if (button) {
      const original = button.textContent
      button.textContent = '✓ Guardado'
      window.setTimeout(() => { button.textContent = original }, 1400)
    }
  })

  mistralSettings.querySelector<HTMLButtonElement>('.ai-mistral-save')?.addEventListener('click', () => {
    const agentId = mistralSettings.querySelector<HTMLInputElement>('.ai-mistral-agent')?.value || ''
    const key = mistralSettings.querySelector<HTMLInputElement>('.ai-mistral-key')?.value || ''
    setMistralConfig(agentId, key || undefined)
    const button = mistralSettings.querySelector<HTMLButtonElement>('.ai-mistral-save')
    if (button) {
      const original = button.textContent
      button.textContent = key || getMistralConfig().apiKey ? '✓ Listo' : 'Agent ID guardado'
      window.setTimeout(() => { button.textContent = original }, 1400)
    }
    const keyInput = mistralSettings.querySelector<HTMLInputElement>('.ai-mistral-key')
    if (keyInput) keyInput.value = ''
  })

  mistralSettings.querySelector<HTMLButtonElement>('.ai-mistral-clear')?.addEventListener('click', () => {
    clearMistralSessionKey()
    const input = mistralSettings.querySelector<HTMLInputElement>('.ai-mistral-key')
    if (input) input.value = ''
  })

  refreshProviderUi()

  const welcome = host.querySelector('.ai-welcome-row')
  if (welcome?.nextSibling) host.insertBefore(panel, welcome.nextSibling)
  else host.prepend(panel)
}

const observer = new MutationObserver(buildProviderPanel)
observer.observe(document.documentElement, { childList: true, subtree: true })
buildProviderPanel()
''')

# Keep the Edge Function source versioned in the repository.
write("supabase/functions/notcan-ai/index.ts", r'''import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

type ContextItem = { title?: string; body?: string; subject?: string; classTitle?: string };
type Mode = "chat" | "summary" | "questions" | "concept-map";
type Provider = "auto" | "mistral" | "free";

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

function cleanText(value: unknown, max = 10000) {
  return String(value ?? "")
    .replace(/<script[\s\S]*?<\/script>/gi, " ")
    .replace(/<style[\s\S]*?<\/style>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&nbsp;/gi, " ")
    .replace(/\s+/g, " ")
    .trim()
    .slice(0, max);
}

function words(text: string) {
  return text.toLocaleLowerCase("es")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .split(/[^a-z0-9áéíóúüñ]+/i)
    .filter((word) => word.length >= 4 && !STOP_WORDS.has(word));
}

function sentences(text: string) {
  return text.split(/(?<=[.!?])\s+|\n+/)
    .map((sentence) => sentence.trim())
    .filter((sentence) => sentence.length >= 35 && sentence.length <= 700);
}

function rankSentences(text: string, prompt: string, limit: number) {
  const query = new Set(words(prompt));
  return sentences(text).slice(0, 180).map((sentence, index) => {
    const sentenceWords = words(sentence);
    const overlap = sentenceWords.reduce((score, word) => score + (query.has(word) ? 3 : 0), 0);
    const richness = new Set(sentenceWords).size / Math.max(1, sentenceWords.length);
    return { sentence, score: overlap + richness + Math.max(0, 1.5 - index * 0.015) };
  }).sort((a, b) => b.score - a.score).slice(0, limit).map((item) => item.sentence);
}

function sourceLabel(item: ContextItem, index: number) {
  const parts = [item.subject, item.classTitle, item.title].filter(Boolean);
  return parts.length ? parts.join(" · ") : `Fuente ${index + 1}`;
}

function normalizeContext(raw: unknown): ContextItem[] {
  return Array.isArray(raw) ? raw.slice(0, 12).map((item: any) => ({
    title: cleanText(item?.title, 200),
    subject: cleanText(item?.subject, 200),
    classTitle: cleanText(item?.classTitle, 200),
    body: cleanText(item?.body, 10000),
  })) : [];
}

function buildSummary(items: ContextItem[], prompt: string) {
  if (!items.length) return "TuNot necesita apuntes, transcripciones o materiales de NotCan para generar un resumen gratuito y fiel a tus fuentes.";
  const lines: string[] = [];
  for (const [index, item] of items.entries()) {
    const top = rankSentences(cleanText(item.body, 9000), prompt, 2);
    if (top.length) lines.push(`• ${sourceLabel(item, index)}: ${top.join(" ")}`);
  }
  return lines.length ? `Resumen de TuNot basado en tus materiales:\n\n${lines.join("\n\n")}` : "No encontré suficiente texto legible en los materiales seleccionados.";
}

function buildQuestions(items: ContextItem[], prompt: string) {
  const selected = rankSentences(items.map((item) => cleanText(item.body, 9000)).filter(Boolean).join(" "), prompt, 10);
  if (!selected.length) return "TuNot necesita material de estudio para crear preguntas sin inventar contenido.";
  return selected.map((sentence, index) => `${index + 1}. **Pregunta:** ¿Qué afirma o explica el material sobre este punto?\n   **Respuesta:** ${sentence.length > 320 ? `${sentence.slice(0, 317)}…` : sentence}`).join("\n\n");
}

function buildConceptMap(items: ContextItem[], prompt: string) {
  if (!items.length) return "TuNot necesita materiales para construir un mapa conceptual fiel a tus fuentes.";
  const central = cleanText(prompt, 120) || items[0]?.subject || items[0]?.title || "Tema de estudio";
  const branches = items.slice(0, 8).map((item, index) => {
    const top = rankSentences(cleanText(item.body, 9000), prompt, 2);
    return `- **${sourceLabel(item, index)}**\n  - ${top[0] || "Sin contenido suficiente"}${top[1] ? `\n  - ${top[1]}` : ""}`;
  });
  return `## ${central}\n\n${branches.join("\n")}`;
}

function buildChat(items: ContextItem[], prompt: string) {
  if (!items.length) return "TuNot está disponible en modo gratuito, pero necesita que actives tus apuntes como contexto para responder sin una API externa.";
  const combined = items.map((item, index) => `${sourceLabel(item, index)}. ${cleanText(item.body, 9000)}`).join(" ");
  const selected = rankSentences(combined, prompt, 7);
  if (!selected.length) return "No encontré en tus materiales información suficiente para responder con seguridad.";
  return `Según tus materiales de NotCan:\n\n${selected.map((sentence) => `• ${sentence}`).join("\n\n")}`;
}

function freeAnswer(mode: Mode, items: ContextItem[], prompt: string) {
  return mode === "summary" ? buildSummary(items, prompt)
    : mode === "questions" ? buildQuestions(items, prompt)
    : mode === "concept-map" ? buildConceptMap(items, prompt)
    : buildChat(items, prompt);
}

function buildMistralPrompt(mode: Mode, items: ContextItem[], prompt: string) {
  const task = mode === "summary" ? "Resume el material con estructura clara y útil para estudiar."
    : mode === "questions" ? "Crea 10 preguntas de estudio con sus respuestas; evita inventar datos."
    : mode === "concept-map" ? "Construye un mapa conceptual textual legible con concepto central, ramas y relaciones."
    : "Responde la consulta del estudiante con precisión académica y claridad pedagógica.";
  const context = items.map((item, index) => `[Fuente ${index + 1}] ${sourceLabel(item, index)}\n${cleanText(item.body, 9000)}`).join("\n\n");
  return `Eres TuNot, asistente académico de NotCan. Responde en español. No inventes citas, páginas, autores ni referencias. Distingue el material proporcionado de conocimiento general y conserva literalmente los textos entre comillas cuando debas citarlos.\n\nTAREA: ${task}\n\nSOLICITUD:\n${prompt}${context ? `\n\nMATERIAL DE NOTCAN:\n${context}` : ""}`;
}

function extractMistralText(root: any): string {
  const outputs = Array.isArray(root?.outputs) ? root.outputs : [];
  const parts: string[] = [];
  for (const output of outputs) {
    if (output?.type && output.type !== "message.output") continue;
    const content = output?.content;
    if (typeof content === "string" && content.trim()) parts.push(content.trim());
    if (Array.isArray(content)) {
      for (const chunk of content) {
        if (typeof chunk === "string" && chunk.trim()) parts.push(chunk.trim());
        else if (chunk && typeof chunk === "object") {
          const text = String(chunk.text ?? chunk.content ?? "").trim();
          if (text) parts.push(text);
        }
      }
    }
  }
  return parts.join("\n").trim();
}

async function askMistral(body: any, mode: Mode, items: ContextItem[], prompt: string) {
  const apiKey = cleanText(body?.mistralApiKey, 500) || Deno.env.get("MISTRAL_API_KEY") || "";
  const agentId = cleanText(body?.mistralAgentId, 500) || Deno.env.get("MISTRAL_AGENT_ID") || "";
  if (!apiKey || !agentId) throw new Error("Configura el Agent ID y la API key de Mistral en NotCan AI.");

  const response = await fetch("https://api.mistral.ai/v1/conversations", {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
      "Accept": "application/json",
    },
    body: JSON.stringify({ agent_id: agentId, inputs: buildMistralPrompt(mode, items, prompt), store: false }),
  });
  const text = await response.text();
  let root: any = {};
  try { root = JSON.parse(text || "{}"); } catch { /* handled below */ }
  if (!response.ok) {
    const message = String(root?.message ?? root?.detail ?? text ?? `HTTP ${response.status}`).slice(0, 600);
    throw new Error(`Mistral (${response.status}): ${message}`);
  }
  const answer = extractMistralText(root);
  if (!answer) throw new Error("Mistral respondió sin contenido de texto.");
  return { answer, model: "Mistral Agent", provider: "mistral", sourceCount: items.length };
}

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ error: "Método no permitido" }, 405);

  try {
    const body = await req.json();
    const prompt = cleanText(body?.prompt, 12000);
    if (!prompt) throw new Error("Escribe una pregunta.");
    const mode = (["chat", "summary", "questions", "concept-map"].includes(String(body?.mode)) ? String(body.mode) : "chat") as Mode;
    const provider = (["auto", "mistral", "free"].includes(String(body?.provider)) ? String(body.provider) : "auto") as Provider;
    const context = normalizeContext(body?.context);
    const hasMistral = Boolean(cleanText(body?.mistralApiKey, 500) && cleanText(body?.mistralAgentId, 500)) || Boolean(Deno.env.get("MISTRAL_API_KEY") && Deno.env.get("MISTRAL_AGENT_ID"));

    if (provider === "mistral" || (provider === "auto" && hasMistral)) {
      return json(await askMistral(body, mode, context, prompt));
    }

    return json({
      answer: freeAnswer(mode, context, prompt),
      model: "TuNot gratuito · fuentes de NotCan",
      provider: "free",
      sourceCount: context.length,
    });
  } catch (error) {
    return json({ error: error instanceof Error ? error.message : String(error) }, 400);
  }
});

const STOP_WORDS = new Set([
  "para", "como", "pero", "porque", "cuando", "donde", "desde", "hasta", "sobre", "entre",
  "este", "esta", "estos", "estas", "esto", "tambien", "tiene", "tienen", "hacer", "puede",
  "pueden", "cual", "cuales", "quien", "quienes", "segun", "todo", "toda", "todos", "todas",
  "unos", "unas", "solo", "sino", "mismo", "misma", "mismos", "mismas", "muy", "mas",
]);
''')

# Small CSS addition for the Mistral action row.
css_path = "web/src/ai-provider-ui.css"
css = read(css_path)
if ".ai-provider-actions" not in css:
    css += "\n.ai-provider-actions { display: flex; gap: 8px; flex-wrap: wrap; }\n.ai-mistral-settings input[type='password'] { font-family: inherit; }\n"
write(css_path, css)

print("Applied current NotCan APK/web fixes")
