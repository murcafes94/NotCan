from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def write(path, content):
    p = ROOT / path
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding="utf-8")


def replace_once(text, old, new, label):
    if old not in text:
        raise RuntimeError(f"Missing patch target: {label}")
    return text.replace(old, new, 1)


def regex_once(text, pattern, repl, label, flags=0):
    out, n = re.subn(pattern, repl, text, count=1, flags=flags)
    if n != 1:
        raise RuntimeError(f"Expected one regex match for {label}, got {n}")
    return out


# ---------------------------------------------------------------------------
# Version
# ---------------------------------------------------------------------------
p = "app/build.gradle.kts"
s = read(p)
s = replace_once(s, 'versionCode = 47\n        versionName = "0.8.24"', 'versionCode = 48\n        versionName = "0.8.25"', "version 0.8.25")
write(p, s)


# ---------------------------------------------------------------------------
# Preferences: remember the exact Android calendar selected by the user.
# ---------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/settings/NotCanPreferences.kt"
s = read(p)
s = replace_once(
    s,
    '    var autoDetectAcademicCues: Boolean\n        get() = prefs.getBoolean(KEY_AUTO_CUES, true)\n        set(value) = prefs.edit().putBoolean(KEY_AUTO_CUES, value).apply()\n',
    '    var autoDetectAcademicCues: Boolean\n        get() = prefs.getBoolean(KEY_AUTO_CUES, true)\n        set(value) = prefs.edit().putBoolean(KEY_AUTO_CUES, value).apply()\n\n    /** CalendarContract id chosen by the user. -1 means automatic (Google first, then device/local). */\n    var calendarId: Long\n        get() = prefs.getLong(KEY_CALENDAR_ID, -1L)\n        set(value) = prefs.edit().putLong(KEY_CALENDAR_ID, value).apply()\n',
    "calendar preference"
)
s = replace_once(s, '        private const val KEY_AUTO_CUES = "auto_detect_academic_cues"\n', '        private const val KEY_AUTO_CUES = "auto_detect_academic_cues"\n        private const val KEY_CALENDAR_ID = "calendar_id"\n', "calendar preference key")
write(p, s)


# ---------------------------------------------------------------------------
# Storage maintenance: delete only known obsolete AI models and stale partials.
# Never touches Gemma/Moonshine/Whisper/current user files.
# ---------------------------------------------------------------------------
write("app/src/main/java/com/notcan/app/storage/StorageMaintenance.kt", r'''package com.notcan.app.storage

import android.app.DownloadManager
import android.content.Context
import java.io.File

object StorageMaintenance {
    data class CleanupResult(val filesRemoved: Int, val bytesFreed: Long)

    private val obsoleteNames = setOf(
        "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        "Qwen3-0.6B-Q8_0.gguf",
        "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        "LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
    )

    /** Safe startup cleanup for local AI engines that NotCan no longer references. */
    fun cleanupObsoleteAi(context: Context): CleanupResult {
        val app = context.applicationContext
        val oldPrefs = app.getSharedPreferences("notcan_study_model", Context.MODE_PRIVATE)
        val oldDownloadId = oldPrefs.getLong("download_id", -1L)
        if (oldDownloadId > 0L) runCatching {
            (app.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(oldDownloadId)
        }
        oldPrefs.edit().clear().apply()

        var filesRemoved = 0
        var bytesFreed = 0L
        val dirs = listOfNotNull(
            app.getExternalFilesDir("models"),
            File(app.filesDir, "models")
        ).distinctBy { it.absolutePath }

        dirs.forEach { dir ->
            if (!dir.exists()) return@forEach
            dir.listFiles().orEmpty().forEach { file ->
                val lower = file.name.lowercase()
                val exactLegacy = file.name in obsoleteNames
                val staleLegacyPartial = (lower.endsWith(".importing") || lower.endsWith(".part") || lower.endsWith(".tmp")) &&
                    ("qwen" in lower || "deepseek" in lower || "lfm" in lower)
                if (file.isFile && (exactLegacy || staleLegacyPartial)) {
                    val size = file.length()
                    if (runCatching { file.delete() }.getOrDefault(false)) {
                        filesRemoved++
                        bytesFreed += size
                    }
                }
            }
        }
        return CleanupResult(filesRemoved, bytesFreed)
    }
}
''')


# ---------------------------------------------------------------------------
# Calendar: all writable calendars, Google-first, chosen account persisted.
# ---------------------------------------------------------------------------
write("app/src/main/java/com/notcan/app/calendar/CalendarSync.kt", r'''package com.notcan.app.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.SubjectScheduleEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.TimeZone

object CalendarSync {
    data class CalendarTarget(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val accountType: String,
        val isPrimary: Boolean
    ) {
        val isGoogle: Boolean get() = accountType.equals("com.google", ignoreCase = true)
        val label: String
            get() = when {
                isGoogle && accountName.isNotBlank() -> "Google · $accountName"
                accountName.isNotBlank() && displayName != accountName -> "$displayName · $accountName"
                displayName.isNotBlank() -> displayName
                else -> "Calendario del dispositivo"
            }
    }

    data class SyncResult(val eventId: Long, val calendar: CalendarTarget)

    fun listWritableCalendars(context: Context): List<CalendarTarget> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.IS_PRIMARY
        )
        val result = mutableListOf<CalendarTarget>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?",
            arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
            val nameCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            val accountCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
            val typeCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)
            val primaryCol = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.IS_PRIMARY)
            while (cursor.moveToNext()) {
                result += CalendarTarget(
                    id = cursor.getLong(idCol),
                    displayName = cursor.getString(nameCol).orEmpty(),
                    accountName = cursor.getString(accountCol).orEmpty(),
                    accountType = cursor.getString(typeCol).orEmpty(),
                    isPrimary = cursor.getInt(primaryCol) == 1
                )
            }
        }
        return result.sortedWith(
            compareByDescending<CalendarTarget> { it.isGoogle }
                .thenByDescending { it.isPrimary }
                .thenBy { it.accountName.lowercase() }
                .thenBy { it.displayName.lowercase() }
        )
    }

    fun preferredTarget(context: Context, preferredId: Long? = null): CalendarTarget? {
        val calendars = listWritableCalendars(context)
        val requested = preferredId?.takeIf { it > 0L }?.let { id -> calendars.firstOrNull { it.id == id } }
        return requested
            ?: calendars.firstOrNull { it.isGoogle && it.isPrimary }
            ?: calendars.firstOrNull { it.isGoogle }
            ?: calendars.firstOrNull { it.isPrimary }
            ?: calendars.firstOrNull()
    }

    fun syncSchedule(
        context: Context,
        cycle: StudyCycleEntity,
        subject: SubjectEntity,
        schedule: SubjectScheduleEntity,
        calendarId: Long? = null
    ): SyncResult? {
        if (cycle.startEpochDay <= 0 || cycle.endEpochDay < cycle.startEpochDay) return null
        val target = preferredTarget(context, calendarId) ?: return null
        val first = AcademicSchedule.allOccurrences(cycle, listOf(subject), listOf(schedule)).firstOrNull() ?: return null
        schedule.calendarEventId?.let { removeEvent(context, it) }

        val zone = ZoneId.systemDefault()
        val until = LocalDate.ofEpochDay(cycle.endEpochDay)
            .plusDays(1)
            .atStartOfDay(zone)
            .minusSeconds(1)
            .withZoneSameInstant(ZoneId.of("UTC"))
            .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"))
        val durationMinutes = schedule.endMinuteOfDay - schedule.startMinuteOfDay

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, target.id)
            put(CalendarContract.Events.TITLE, subject.name)
            put(CalendarContract.Events.DESCRIPTION, "Horario académico sincronizado por NotCan")
            put(CalendarContract.Events.DTSTART, first.startEpochMs)
            put(CalendarContract.Events.DURATION, "PT${durationMinutes}M")
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.RRULE, "FREQ=WEEKLY;UNTIL=$until")
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        }
        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = ContentUris.parseId(eventUri)
        val reminder = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, schedule.reminderMinutesBefore.coerceAtLeast(0))
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminder)
        return SyncResult(eventId, target)
    }

    fun removeEvent(context: Context, eventId: Long) {
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        context.contentResolver.delete(uri, null, null)
    }
}
''')


# ---------------------------------------------------------------------------
# Native NotCan reminders: do not cancel/recreate the entire semester unless
# the actual schedule changed. Unique work prevents duplicates.
# ---------------------------------------------------------------------------
write("app/src/main/java/com/notcan/app/calendar/ReminderScheduler.kt", r'''package com.notcan.app.calendar

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.notcan.app.data.local.StudyCycleEntity
import com.notcan.app.data.local.SubjectEntity
import com.notcan.app.data.local.SubjectScheduleEntity
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object ReminderScheduler {
    private const val TAG = "notcan-semester-reminders"
    private const val PREFS = "notcan_reminder_scheduler"
    private const val KEY_SIGNATURE = "signature"

    fun reschedule(
        context: Context,
        cycle: StudyCycleEntity?,
        subjects: List<SubjectEntity>,
        schedules: List<SubjectScheduleEntity>
    ) {
        val workManager = WorkManager.getInstance(context)
        if (cycle == null) {
            workManager.cancelAllWorkByTag(TAG)
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
            return
        }

        val signature = signature(cycle, schedules)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SIGNATURE, null) == signature) return

        workManager.cancelAllWorkByTag(TAG)
        val now = System.currentTimeMillis()
        AcademicSchedule.allOccurrences(cycle, subjects, schedules).forEach { occurrence ->
            val reminderAt = occurrence.startEpochMs - occurrence.schedule.reminderMinutesBefore * 60_000L
            val delay = reminderAt - now
            if (delay <= 0L) return@forEach
            val input = Data.Builder()
                .putString(ClassReminderWorker.KEY_SUBJECT, occurrence.subject.name)
                .putString(ClassReminderWorker.KEY_TIME, AcademicSchedule.formatMinutes(occurrence.schedule.startMinuteOfDay))
                .putInt(ClassReminderWorker.KEY_NOTIFICATION_ID, (occurrence.schedule.id + occurrence.date).hashCode())
                .build()
            val uniqueName = "notcan-reminder-${occurrence.schedule.id}-${occurrence.date}"
            val request = OneTimeWorkRequestBuilder<ClassReminderWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(input)
                .addTag(TAG)
                .build()
            workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.REPLACE, request)
        }
        prefs.edit().putString(KEY_SIGNATURE, signature).apply()
    }

    private fun signature(cycle: StudyCycleEntity, schedules: List<SubjectScheduleEntity>): String {
        val raw = buildString {
            append(cycle.id).append('|').append(cycle.startEpochDay).append('|').append(cycle.endEpochDay)
            schedules.sortedBy { it.id }.forEach { s ->
                append('|').append(s.id)
                    .append(':').append(s.subjectId)
                    .append(':').append(s.weekdayIso)
                    .append(':').append(s.startMinuteOfDay)
                    .append(':').append(s.endMinuteOfDay)
                    .append(':').append(s.reminderMinutesBefore)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
            .take(12).joinToString("") { "%02x".format(it) }
    }
}
''')


# ---------------------------------------------------------------------------
# ViewModel: remove obsolete Qwen lifecycle/polling; cloud-link documents.
# ---------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt"
s = read(p)
s = s.replace("import android.app.Application\n", "import android.app.Application\nimport android.content.Intent\n")
s = s.replace("import com.notcan.app.localai.StudyModelManager\n", "")
s = s.replace("import com.notcan.app.localai.StudyModelState\n", "")
s = replace_once(s, "    private val sourceStore = ClassSourceStore(application)\n    private val studyModelManager = StudyModelManager(application)\n    private val whisperModelManager = WhisperModelManager(application)\n", "    private val sourceStore = ClassSourceStore(application)\n    private val whisperModelManager = WhisperModelManager(application)\n", "remove legacy model manager")
s = regex_once(s, r'\n    private val _aiConfigured = MutableStateFlow\(aiService\.isConfigured\(\)\)\n    val aiConfigured: StateFlow<Boolean> = _aiConfigured\.asStateFlow\(\)\n\n    private val _studyModelState = .*?val studyModelProgress: StateFlow<Int\?> = _studyModelProgress\.asStateFlow\(\)\n', '\n', "remove legacy ai configured/model state", flags=re.S)
s = replace_once(
    s,
    '        viewModelScope.launch(Dispatchers.IO) {\n            while (isActive) {\n                _whisperModelState.value = whisperModelManager.state()\n                _whisperModelProgress.value = whisperModelManager.progressPercent()\n                _studyModelState.value = studyModelManager.state()\n                _studyModelProgress.value = studyModelManager.progressPercent()\n                _aiConfigured.value = _studyModelState.value == StudyModelState.INSTALLED\n                val downloading = _whisperModelState.value == WhisperModelState.DOWNLOADING ||\n                    _studyModelState.value == StudyModelState.DOWNLOADING\n                delay(if (downloading) 1_500 else 8_000)\n            }\n        }',
    '        viewModelScope.launch(Dispatchers.IO) {\n            while (isActive) {\n                _whisperModelState.value = whisperModelManager.state()\n                _whisperModelProgress.value = whisperModelManager.progressPercent()\n                val downloading = _whisperModelState.value == WhisperModelState.DOWNLOADING\n                delay(if (downloading) 1_500 else 30_000)\n            }\n        }',
    "reduce background model polling"
)
s = regex_once(s, r'\n    fun downloadStudyModel\(\) \{.*?\n    fun transcribeAudioLocal', '\n    fun transcribeAudioLocal', "remove legacy study model actions", flags=re.S)
old_import = '''    fun importDocument(classSessionId: String, uri: Uri) {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = application.contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
                ?: "documento_${System.currentTimeMillis()}"
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val type = resolveDocumentType(displayName, mimeType)
            val id = UUID.randomUUID().toString()
            val destinationDir = File(application.filesDir, "documents/$classSessionId").apply { mkdirs() }
            val destination = File(destinationDir, "${id.take(8)}_${sanitizeFileName(displayName)}")
            resolver.openInputStream(uri)?.use { source -> destination.outputStream().use { target -> source.copyTo(target, 64 * 1024) } } ?: return@launch
            repository.saveDocument(DocumentResourceEntity(id, classSessionId, displayName, destination.absolutePath, mimeType, type, System.currentTimeMillis()))
        }
    }
'''
new_import = '''    fun importDocument(classSessionId: String, uri: Uri) {
        val application = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            val resolver = application.contentResolver
            val displayName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "documento_${System.currentTimeMillis()}"
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val type = resolveDocumentType(displayName, mimeType)
            val id = UUID.randomUUID().toString()

            // OpenDocument URIs (including Google Drive) can be kept as persistent references.
            // This avoids a second permanent copy inside NotCan and lets compatible editors
            // save directly back to the cloud provider.
            val readFlag = Intent.FLAG_GRANT_READ_URI_PERMISSION
            val writeFlag = Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val persistent = runCatching {
                resolver.takePersistableUriPermission(uri, readFlag or writeFlag)
                true
            }.getOrElse {
                runCatching {
                    resolver.takePersistableUriPermission(uri, readFlag)
                    true
                }.getOrDefault(false)
            }

            if (persistent) {
                repository.saveDocument(
                    DocumentResourceEntity(id, classSessionId, displayName, uri.toString(), mimeType, type, System.currentTimeMillis())
                )
                return@launch
            }

            // Rare providers without persistable URI permission keep the previous safe fallback.
            val destinationDir = File(application.filesDir, "documents/$classSessionId").apply { mkdirs() }
            val destination = File(destinationDir, "${id.take(8)}_${sanitizeFileName(displayName)}")
            resolver.openInputStream(uri)?.use { source ->
                destination.outputStream().use { target -> source.copyTo(target, 64 * 1024) }
            } ?: return@launch
            repository.saveDocument(
                DocumentResourceEntity(id, classSessionId, displayName, destination.absolutePath, mimeType, type, System.currentTimeMillis())
            )
        }
    }
'''
s = replace_once(s, old_import, new_import, "cloud document import")
# Add calendar context to pedagogical mode, without polluting normal questions.
s = replace_once(
    s,
    '                val notesText = notePages.value.joinToString("\\n\\n") { "${it.title}\\n${it.body}" }\n                val subjectName = subjects.value.firstOrNull { it.id == _selectedSubjectId.value }?.name\n',
    '                val baseNotes = notePages.value.joinToString("\\n\\n") { "${it.title}\\n${it.body}" }\n                val subjectName = subjects.value.firstOrNull { it.id == _selectedSubjectId.value }?.name\n                val notesText = if (question.contains(NotCanAiService.PEDAGOGY_MARKER)) {\n                    val subjectId = _selectedSubjectId.value\n                    val calendarText = schedules.value.filter { it.subjectId == subjectId }.joinToString("\\n") { schedule ->\n                        "${AcademicSchedule.weekdayLabel(schedule.weekdayIso)} ${AcademicSchedule.formatMinutes(schedule.startMinuteOfDay)}–${AcademicSchedule.formatMinutes(schedule.endMinuteOfDay)}"\n                    }\n                    buildString {\n                        append(baseNotes)\n                        if (calendarText.isNotBlank()) {\n                            appendLine("\\n\\n[HORARIO SEMANAL DE ESTA MATERIA]")\n                            append(calendarText)\n                        }\n                    }\n                } else baseNotes\n',
    "pedagogy calendar context"
)
s = s.replace("import com.notcan.app.calendar.PlannedClassOccurrence\n", "import com.notcan.app.calendar.AcademicSchedule\nimport com.notcan.app.calendar.PlannedClassOccurrence\n")
s = s.replace("                _aiResult.value = finalResult\n                _aiConfigured.value = true\n", "                _aiResult.value = finalResult\n")
s = s.replace("                _aiConfigured.value = aiService.isConfigured()\n                _aiError.value", "                _aiError.value")
write(p, s)


# ---------------------------------------------------------------------------
# MainActivity: safe cleanup, cloud URI open/edit, selected calendar.
# ---------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/MainActivity.kt"
s = read(p)
s = s.replace("import android.content.Intent\n", "import android.content.Intent\nimport android.net.Uri\n")
s = s.replace("import com.notcan.app.settings.NotCanPreferences\n", "import com.notcan.app.settings.NotCanPreferences\nimport com.notcan.app.storage.StorageMaintenance\n")
s = s.replace("import kotlinx.coroutines.launch\n", "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.launch\n")
s = replace_once(
    s,
    '        super.onCreate(savedInstanceState)\n        WindowCompat.setDecorFitsSystemWindows(window, true)\n',
    '        super.onCreate(savedInstanceState)\n        WindowCompat.setDecorFitsSystemWindows(window, true)\n        lifecycleScope.launch(Dispatchers.IO) { StorageMaintenance.cleanupObsoleteAi(this@MainActivity) }\n',
    "startup cleanup"
)
s = s.replace('                val aiConfigured = studyViewModel.aiConfigured.collectAsStateWithLifecycle().value\n', '')
s = s.replace('                val studyModelState = studyViewModel.studyModelState.collectAsStateWithLifecycle().value\n                val studyModelProgress = studyViewModel.studyModelProgress.collectAsStateWithLifecycle().value\n', '')
s = s.replace('                                subtitle = "Documento local · ${document.documentType}",', '                                subtitle = if (document.localPath.startsWith("content://")) "Documento en nube · ${document.documentType}" else "Documento local · ${document.documentType}",')
s = s.replace('                            configured = aiConfigured,\n', '')
s = s.replace('                            studyModelState = studyModelState,\n                            studyModelProgress = studyModelProgress,\n', '')
s = s.replace('                            onDownloadStudyModel = studyViewModel::downloadStudyModel,\n                            onRemoveStudyModel = studyViewModel::removeStudyModel,\n', '')
old_sync = '''    private fun performCalendarSync(scheduleId: String) {
        val schedule = studyViewModel.schedules.value.firstOrNull { it.id == scheduleId } ?: return
        val cycle = studyViewModel.cycles.value.firstOrNull { it.id == schedule.cycleId } ?: return
        val subject = studyViewModel.subjects.value.firstOrNull { it.id == schedule.subjectId } ?: return
        try {
            val eventId = CalendarSync.syncSchedule(this, cycle, subject, schedule)
            if (eventId != null) {
                studyViewModel.setScheduleCalendarEvent(schedule.id, eventId)
                Toast.makeText(this, "${subject.name} sincronizada con el calendario", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "No encontré un calendario editable en el dispositivo", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "No se pudo sincronizar: ${t.message ?: "error"}", Toast.LENGTH_LONG).show()
        }
    }
'''
new_sync = '''    private fun performCalendarSync(scheduleId: String) {
        val schedule = studyViewModel.schedules.value.firstOrNull { it.id == scheduleId } ?: return
        val cycle = studyViewModel.cycles.value.firstOrNull { it.id == schedule.cycleId } ?: return
        val subject = studyViewModel.subjects.value.firstOrNull { it.id == schedule.subjectId } ?: return
        try {
            val result = CalendarSync.syncSchedule(
                this,
                cycle,
                subject,
                schedule,
                preferences.calendarId.takeIf { it > 0L }
            )
            if (result != null) {
                preferences.calendarId = result.calendar.id
                studyViewModel.setScheduleCalendarEvent(schedule.id, result.eventId)
                Toast.makeText(this, "${subject.name} · ${result.calendar.label}", Toast.LENGTH_SHORT).show()
            } else Toast.makeText(this, "No encontré un calendario editable en el dispositivo", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Toast.makeText(this, "No se pudo sincronizar: ${t.message ?: "error"}", Toast.LENGTH_LONG).show()
        }
    }
'''
s = replace_once(s, old_sync, new_sync, "calendar chosen account")
old_doc = '''    private fun requestDocumentImport(classSessionId: String) {
        pendingDocumentClassId = classSessionId
        documentLauncher.launch(arrayOf("application/pdf", "application/epub+zip", "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
    }

    private fun openDocument(document: DocumentResourceEntity) {
        val file = File(document.localPath)
        if (!file.exists()) {
            Toast.makeText(this, "El archivo local ya no existe", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "No hay una aplicación compatible para abrir este archivo", Toast.LENGTH_SHORT).show() }
    }
'''
new_doc = '''    private fun requestDocumentImport(classSessionId: String) {
        pendingDocumentClassId = classSessionId
        // Android's Storage Access Framework exposes every configured Drive account plus
        // Xiaomi/local and other document providers. The selected file is referenced, not copied.
        documentLauncher.launch(arrayOf("*/*"))
    }

    private fun openDocument(document: DocumentResourceEntity) {
        if (document.localPath.startsWith("content://")) {
            val uri = Uri.parse(document.localPath)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            val edit = Intent(Intent.ACTION_EDIT).setDataAndType(uri, document.mimeType).addFlags(flags)
            try {
                startActivity(edit)
                return
            } catch (_: ActivityNotFoundException) {
                val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType).addFlags(flags)
                try { startActivity(view) }
                catch (_: ActivityNotFoundException) { Toast.makeText(this, "No hay una aplicación compatible para abrir este documento", Toast.LENGTH_SHORT).show() }
                return
            }
        }

        val file = File(document.localPath)
        if (!file.exists()) {
            Toast.makeText(this, "El archivo local ya no existe", Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, document.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(intent) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "No hay una aplicación compatible para abrir este archivo", Toast.LENGTH_SHORT).show() }
    }
'''
s = replace_once(s, old_doc, new_doc, "SAF cloud documents")
write(p, s)


# ---------------------------------------------------------------------------
# TuNot screen: remove retired model controls; improve study/assistance prompts.
# ---------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt"
s = read(p)
s = s.replace("import com.notcan.app.localai.StudyModelState\n", "")
s = s.replace('    configured: Boolean,\n', '', 1)
s = s.replace('    studyModelState: StudyModelState,\n    studyModelProgress: Int?,\n', '', 1)
s = s.replace('    onDownloadStudyModel: () -> Unit,\n    onRemoveStudyModel: () -> Unit,\n', '', 1)
s = regex_once(s, r'    @Suppress\("UNUSED_VARIABLE"\)\n    val legacy = listOf\(configured, studyModelState, studyModelProgress, onDownloadStudyModel, onRemoveStudyModel\)\n', '', "remove legacy ai screen shim")
s = s.replace('    val emphasized = engineLabel.contains("Mistral") || engineLabel.contains("Qwen2.5")', '    val emphasized = engineLabel.contains("Mistral") || engineLabel.contains("Gemma 4")')
s = s.replace(
    'StudyTool("Resumen de clase", "Ideas principales, conceptos y estructura", Icons.Default.GraphicEq, "Haz un resumen estructurado de esta clase. Separa ideas principales, conceptos clave, definiciones y relaciones."),',
    'StudyTool("Resumen de clase", "Ideas principales, conceptos y estructura", Icons.Default.GraphicEq, "Haz un resumen estructurado para estudiar esta clase: idea central, 5–8 ideas principales, conceptos/definiciones, relaciones y 3 preguntas de comprobación. No repitas contenido."),'
)
s = s.replace(
    '            "Actúa como pedagogo y ayúdame a preparar un examen de esta materia. Propón una estrategia por etapas usando recuperación activa, práctica y repaso; adapta el plan al material disponible."',
    '            "Actúa como pedagogo académico y ayúdame a preparar un examen de esta materia. Usa primero el material disponible y mi horario semanal. Propón una estrategia concreta por etapas con recuperación activa, práctica, repaso y criterios para saber si ya domino cada bloque."'
)
s = s.replace(
    '            "Diseña un plan de repaso eficiente para esta materia con recuperación activa, intervalos de repaso y comprobaciones breves de dominio."',
    '            "Diseña un plan de repaso eficiente para esta materia usando mi material disponible y, cuando conste, mi horario semanal. Incluye recuperación activa, intervalos de repaso, duración aproximada de cada bloque y comprobaciones breves de dominio."'
)
# Add exam-oral and gap-analysis tools before plan de repaso.
needle = '''        AssistanceTool(
            "Plan de repaso",
            "Repasar sin releer todo desde cero",
            "Diseña un plan de repaso eficiente para esta materia usando mi material disponible y, cuando conste, mi horario semanal. Incluye recuperación activa, intervalos de repaso, duración aproximada de cada bloque y comprobaciones breves de dominio."
        )'''
replacement = '''        AssistanceTool(
            "Preparar examen oral",
            "Explicar con orden, precisión y seguridad",
            "Prepárame para un examen oral de esta materia. Organiza los temas en respuestas de 2–5 minutos, señala conceptos que debo decir con precisión y luego hazme una pregunta de práctica cada vez."
        ),
        AssistanceTool(
            "Detectar lagunas",
            "Encontrar qué entiendo y qué todavía no",
            "Ayúdame a detectar lagunas de aprendizaje usando el material disponible. Propón una comprobación breve por conceptos y prioriza lo que debo corregir antes de seguir avanzando."
        ),
        AssistanceTool(
            "Plan de repaso",
            "Repasar sin releer todo desde cero",
            "Diseña un plan de repaso eficiente para esta materia usando mi material disponible y, cuando conste, mi horario semanal. Incluye recuperación activa, intervalos de repaso, duración aproximada de cada bloque y comprobaciones breves de dominio."
        )'''
s = replace_once(s, needle, replacement, "assistance new tools")
write(p, s)


# ---------------------------------------------------------------------------
# Settings: no Qwen/LFM manager, slower idle refresh, choose Google/Xiaomi calendar.
# ---------------------------------------------------------------------------
p = "app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt"
s = read(p)
s = s.replace("package com.notcan.app.ui.settings\n\n", "package com.notcan.app.ui.settings\n\nimport android.Manifest\nimport android.content.pm.PackageManager\n")
s = s.replace("import androidx.compose.ui.platform.LocalContext\n", "import androidx.compose.ui.platform.LocalContext\nimport androidx.core.content.ContextCompat\n")
s = s.replace("import com.notcan.app.ai.MistralCredentialsStore\n", "import com.notcan.app.ai.MistralCredentialsStore\nimport com.notcan.app.calendar.CalendarSync\n")
s = s.replace("import com.notcan.app.localai.StudyModelManager\n", "")
s = s.replace("    val studyManager = remember(context) { StudyModelManager(context.applicationContext) }\n", "")
s = s.replace("            delay(1_500)\n", "            delay(8_000)\n", 1)
s = regex_once(s, r'    val oldQwenInstalled = remember\(refreshTick\) \{.*?\n    \}\n    val gemmaState', '    val gemmaState', "remove qwen status", flags=re.S)
s = regex_once(s, r'    val legacyLfmInstalled = remember\(refreshTick\) \{.*?\n    \}\n    val mistralConfigured', '    val mistralConfigured', "remove lfm status", flags=re.S)
# Insert calendar state after mistralConfigured.
s = replace_once(
    s,
    '    val mistralConfigured = hasSavedKey && agentId.trim().isNotBlank()\n',
    '''    val mistralConfigured = hasSavedKey && agentId.trim().isNotBlank()
    val calendarPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
    val calendarTargets = remember(refreshTick, calendarPermission) {
        if (calendarPermission) runCatching { CalendarSync.listWritableCalendars(context) }.getOrDefault(emptyList()) else emptyList()
    }
    var selectedCalendarId by remember { mutableStateOf(preferences.calendarId) }
''',
    "calendar settings state"
)
# Insert calendar card after cycle management.
insert_after = '        CycleManagementSection(cycles)\n\n'
calendar_card = '''        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Calendario y recordatorios", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("Google primero; Xiaomi/local queda como respaldo.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!calendarPermission) {
                    Text("Concede acceso al calendario la primera vez que pulses Sincronizar en Calendario. Después podrás elegir aquí cualquier cuenta disponible.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                } else if (calendarTargets.isEmpty()) {
                    Text("No hay calendarios editables visibles en el dispositivo.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                } else {
                    calendarTargets.forEach { target ->
                        FilterChip(
                            selected = selectedCalendarId == target.id || (selectedCalendarId <= 0L && target == CalendarSync.preferredTarget(context)),
                            onClick = {
                                selectedCalendarId = target.id
                                preferences.calendarId = target.id
                            },
                            label = { Text(target.label) }
                        )
                    }
                    Text("Los eventos se guardan en el proveedor elegido y usan sus propias notificaciones. NotCan mantiene además su recordatorio académico.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

'''
s = replace_once(s, insert_after, insert_after + calendar_card, "calendar settings card")
# Remove legacy cleanup card inside Components offline.
s = regex_once(s, r'\n                if \(oldQwenInstalled \|\| legacyLfmInstalled\) \{.*?\n                \}\n            \}\n        \}\n\n        Card\(colors = CardDefaults\.cardColors\(containerColor = NotCanSurface\), shape = RoundedCornerShape\(16\.dp\)\) \{', '\n            }\n        }\n\n        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {', "remove legacy model cleanup card", flags=re.S)
write(p, s)


# ---------------------------------------------------------------------------
# Remove retired Qwen/LFM implementation from source tree.
# ---------------------------------------------------------------------------
legacy_file = ROOT / "app/src/main/java/com/notcan/app/localai/StudyModelManager.kt"
if legacy_file.exists():
    legacy_file.unlink()


# ---------------------------------------------------------------------------
# Debug workflow metadata: stop publishing stale v0.8.20/Qwen descriptions.
# ---------------------------------------------------------------------------
p = ".github/workflows/android-debug.yml"
s = read(p)
s = s.replace("notcan-v0.8.20-gemma4-litert-apk", "notcan-v0.8.25-audit-cloud-apk")
s = s.replace('TAG="v0.8.20-gemma-litert-test"', 'TAG="v0.8.25-audit-cloud-test"')
s = s.replace('"$APK#NotCan-v0.8.20-Gemma4-LiteRT.apk"', '"$APK#NotCan-v0.8.25-Audit-Cloud.apk"')
s = s.replace('--title "NotCan v0.8.20 · Gemma 4 LiteRT local"', '--title "NotCan v0.8.25 · auditoría, calendario y nube"')
s = regex_once(s, r'--notes "Primera prueba de TuNot.*?"\n', '--notes "Auditoría de almacenamiento y rendimiento: elimina motores locales retirados, conserva LiteRT 0.11, optimiza recordatorios, permite elegir cualquier calendario Google/Xiaomi disponible y enlaza documentos de Drive mediante Storage Access Framework sin copia permanente cuando el proveedor lo permite."\n', "rolling release notes")
write(p, s)


# ---------------------------------------------------------------------------
# Static audit guards. These fail CI before Gradle if a retired runtime remains wired.
# ---------------------------------------------------------------------------
for path in (ROOT / "app/src/main/java").rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    if "StudyModelManager" in text or "StudyModelState" in text:
        raise RuntimeError(f"Retired study model reference remains in {path.relative_to(ROOT)}")

print("v0.8.25 audit/cloud patch applied")
