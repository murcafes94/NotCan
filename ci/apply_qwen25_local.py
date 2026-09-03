from pathlib import Path

# -----------------------------------------------------------------------------
# Qwen2.5 1.5B Instruct Q4_K_M becomes the optional local generative TuNot model.
# LFM2.5 is kept as a removable legacy file until Qwen is validated on-device.
# -----------------------------------------------------------------------------
Path('app/src/main/java/com/notcan/app/localai/StudyModelManager.kt').write_text(r'''package com.notcan.app.localai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

object StudyModelSpec {
    const val DISPLAY_NAME = "TuNot offline · Qwen2.5"
    const val MODEL_NAME = "Qwen2.5 1.5B Instruct Q4_K_M"
    const val FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val DOWNLOAD_URL = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf?download=true"
    const val APPROX_BYTES = 1_120_000_000L
    const val MIN_VALID_BYTES = 1_000_000_000L
    const val LICENSE = "Apache-2.0"
    const val SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"

    const val LEGACY_LFM_FILE_NAME = "LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
    const val LEGACY_QWEN_FILE_NAME = "Qwen3-0.6B-Q8_0.gguf"
    const val LEGACY_DEEPSEEK_FILE_NAME = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"
}

enum class StudyModelState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED
}

class StudyModelManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("notcan_study_model", Context.MODE_PRIVATE)

    fun modelFile(): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        dir.mkdirs()
        return File(dir, StudyModelSpec.FILE_NAME)
    }

    private fun modelDir(): File = modelFile().parentFile ?: File(context.filesDir, "models")

    fun legacyLfmFile(): File = File(modelDir(), StudyModelSpec.LEGACY_LFM_FILE_NAME)

    fun hasLegacyLfmModel(): Boolean = legacyLfmFile().let { it.exists() && it.length() >= 650_000_000L }

    fun removeLegacyLfmModel(): Boolean {
        val file = legacyLfmFile()
        return !file.exists() || file.delete()
    }

    private fun olderLegacyModelFiles(): List<File> = listOf(
        File(modelDir(), StudyModelSpec.LEGACY_QWEN_FILE_NAME),
        File(modelDir(), StudyModelSpec.LEGACY_DEEPSEEK_FILE_NAME)
    )

    fun state(): StudyModelState {
        if (isValidModel(modelFile())) return StudyModelState.INSTALLED

        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0L) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_RUNNING ||
                        status == DownloadManager.STATUS_PENDING ||
                        status == DownloadManager.STATUS_PAUSED
                    ) return StudyModelState.DOWNLOADING
                }
            }
        }
        return StudyModelState.NOT_INSTALLED
    }

    fun enqueueDownload(): Long {
        if (state() == StudyModelState.INSTALLED) return -1L
        val existingId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (existingId > 0L && state() == StudyModelState.DOWNLOADING) return existingId

        val destination = modelFile()
        if (destination.exists()) destination.delete()

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(StudyModelSpec.DOWNLOAD_URL))
            .setTitle("NotCan · TuNot offline")
            .setDescription("Qwen2.5 1.5B Instruct Q4_K_M · aprox. 1.12 GB · funciona sin internet")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(context, "models", StudyModelSpec.FILE_NAME)
        return manager.enqueue(request).also { id -> prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply() }
    }

    fun importExistingModel(uri: Uri): Boolean {
        val destination = modelFile()
        val staging = File(destination.parentFile, "${destination.name}.importing")
        staging.delete()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("No se pudo abrir el archivo seleccionado")

        try {
            input.buffered().use { source ->
                staging.outputStream().buffered().use { output -> source.copyTo(output) }
            }
            if (!isValidModel(staging)) {
                throw IllegalArgumentException("El archivo no parece ser Qwen2.5 1.5B Instruct Q4_K_M en formato GGUF o está incompleto")
            }
            if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("No se pudo reemplazar el modelo local anterior")
            }
            if (!staging.renameTo(destination)) {
                staging.copyTo(destination, overwrite = true)
                staging.delete()
            }
            if (!isValidModel(destination)) {
                destination.delete()
                throw IllegalStateException("El modelo importado no pudo validarse")
            }
            val downloadId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
            if (downloadId > 0L) runCatching {
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(downloadId)
            }
            prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
            return true
        } catch (t: Throwable) {
            staging.delete()
            throw t
        }
    }

    fun progressPercent(): Int? {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L) return null
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            if (downloaded < 0L || total <= 0L) return null
            return ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
        }
        return null
    }

    fun removeModel(): Boolean {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0L) runCatching {
            (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
        }
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        val currentDeleted = !modelFile().exists() || modelFile().delete()
        val oldDeleted = olderLegacyModelFiles().all { !it.exists() || it.delete() }
        // Deliberately do not delete LFM2.5 here. It has its own explicit cleanup action.
        return currentDeleted && oldDeleted
    }

    private fun isValidModel(file: File): Boolean {
        if (!file.exists() || file.length() < StudyModelSpec.MIN_VALID_BYTES) return false
        return runCatching {
            file.inputStream().buffered().use { input ->
                val header = ByteArray(4)
                input.read(header) == 4 && header.contentEquals(
                    byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte())
                )
            }
        }.getOrDefault(false)
    }

    companion object { private const val KEY_DOWNLOAD_ID = "download_id" }
}
''')

# -----------------------------------------------------------------------------
# Qwen local engine. The llama.cpp Android bridge reads the GGUF chat template.
# -----------------------------------------------------------------------------
Path('app/src/main/java/com/notcan/app/ai/LocalQwenTuNotEngine.kt').write_text(r'''package com.notcan.app.ai

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.notcan.app.localai.StudyModelManager
import com.notcan.app.localai.StudyModelState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** On-device TuNot brain backed by Qwen2.5 1.5B Instruct Q4_K_M + llama.cpp. */
class LocalQwenTuNotEngine(context: Context) {
    private val appContext = context.applicationContext
    private val modelManager = StudyModelManager(appContext)
    private val mutex = Mutex()

    fun isAvailable(): Boolean = runCatching {
        modelManager.state() == StudyModelState.INSTALLED
    }.getOrDefault(false)

    suspend fun answer(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String,
        strictSources: Boolean
    ): String = mutex.withLock {
        check(isAvailable()) { "Qwen2.5 no está instalado" }
        val engine = AiChat.getInferenceEngine(appContext)
        ensureModelReady(engine)

        val sourceContext = buildString {
            subjectName?.takeIf { it.isNotBlank() }?.let { appendLine("Materia: $it") }
            if (notes.isNotBlank()) {
                appendLine("\n[Apuntes]")
                appendLine(notes.takeLast(MAX_SOURCE_PART_CHARS))
            }
            if (transcript.isNotBlank()) {
                appendLine("\n[Transcripción]")
                appendLine(transcript.takeLast(MAX_SOURCE_PART_CHARS))
            }
        }.takeLast(MAX_SOURCE_CHARS)

        val prompt = buildString {
            if (strictSources) {
                appendLine("Responde SOLO con las fuentes incluidas. Si el dato no consta, dilo claramente.")
            } else {
                appendLine("Usa el material de clase cuando sea pertinente. Puedes explicar con conocimiento general fiable, sin presentarlo como cita de los apuntes.")
            }
            if (sourceContext.isNotBlank()) {
                appendLine("\n--- MATERIAL DE NOTCAN ---")
                appendLine(sourceContext)
                appendLine("--- FIN DEL MATERIAL ---")
            }
            appendLine("\nPregunta del estudiante:")
            append(question.trim())
        }

        val output = StringBuilder()
        withTimeout(GENERATION_TIMEOUT_MS) {
            engine.sendUserPrompt(prompt, PREDICT_TOKENS).collect { token -> output.append(token) }
        }
        sanitizeModelOutput(output.toString())
            .ifBlank { error("Qwen2.5 no produjo texto utilizable") }
    }

    private fun sanitizeModelOutput(raw: String): String = raw
        .replace(SPECIAL_TOKEN_REGEX, "")
        .replace("<|im_start|>", "")
        .replace("<|im_end|>", "")
        .replace("<|endoftext|>", "")
        .trim()

    private suspend fun ensureModelReady(engine: InferenceEngine) {
        var state = withTimeout(INIT_TIMEOUT_MS) {
            engine.state.first {
                it is InferenceEngine.State.Initialized ||
                    it is InferenceEngine.State.ModelReady ||
                    it is InferenceEngine.State.Error
            }
        }
        if (state is InferenceEngine.State.Error) {
            runCatching { engine.cleanUp() }
            state = withTimeout(INIT_TIMEOUT_MS) {
                engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.ModelReady }
            }
        }
        if (state is InferenceEngine.State.ModelReady) return
        engine.loadModel(modelManager.modelFile().absolutePath)
        engine.setSystemPrompt(SYSTEM_PROMPT)
    }

    companion object {
        const val MODEL_LABEL = "Qwen2.5 1.5B · local"
        private const val MAX_SOURCE_PART_CHARS = 3_200
        private const val MAX_SOURCE_CHARS = 6_400
        private const val PREDICT_TOKENS = 512
        private const val INIT_TIMEOUT_MS = 120_000L
        private const val GENERATION_TIMEOUT_MS = 180_000L
        private val SPECIAL_TOKEN_REGEX = Regex("""<\|[^>]+\|>""")
        private val SYSTEM_PROMPT = """
            Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.
            Responde en español claro, natural, preciso y útil para estudiar.
            Prioriza fidelidad a los apuntes y transcripciones proporcionados.
            No inventes citas, páginas, autores, fechas ni referencias.
            Cuando se trabaje solo con fuentes, no añadas información que no esté en ellas.
            Para teología católica, distingue enseñanza oficial, disciplina, opinión teológica e interpretación académica.
            No muestres cadena de pensamiento. Responde directamente con Markdown sencillo.
        """.trimIndent()
    }
}
''')

old_lfm = Path('app/src/main/java/com/notcan/app/ai/LocalLfmTuNotEngine.kt')
if old_lfm.exists():
    old_lfm.unlink()

# -----------------------------------------------------------------------------
# Preferences: migrate the selected LFM option to Qwen and use a generic error.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/settings/NotCanPreferences.kt')
s = p.read_text()
s = s.replace(
'''    var aiEnginePreference: String
        get() = prefs.getString(KEY_AI_ENGINE, "Automático") ?: "Automático"
        set(value) = prefs.edit().putString(KEY_AI_ENGINE, value).apply()

    var lastLfmError: String
        get() = prefs.getString(KEY_LAST_LFM_ERROR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_LFM_ERROR, value.take(500)).apply()
''',
'''    var aiEnginePreference: String
        get() {
            val stored = prefs.getString(KEY_AI_ENGINE, "Automático") ?: "Automático"
            return if (stored == "LFM2.5 local") "Qwen2.5 local" else stored
        }
        set(value) = prefs.edit().putString(KEY_AI_ENGINE, value).apply()

    var lastLocalAiError: String
        get() = prefs.getString(KEY_LAST_LOCAL_AI_ERROR, "") ?: prefs.getString(KEY_LAST_LFM_ERROR, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_LAST_LOCAL_AI_ERROR, value.take(500)).apply()
''')
s = s.replace(
'        private const val KEY_LAST_LFM_ERROR = "last_lfm_error"\n',
'        private const val KEY_LAST_LFM_ERROR = "last_lfm_error" // migration only\n        private const val KEY_LAST_LOCAL_AI_ERROR = "last_local_ai_error"\n'
)
p.write_text(s)

# -----------------------------------------------------------------------------
# TuNot routing: Mistral -> Qwen -> Local basic.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ai/NotCanAiService.kt')
s = p.read_text()
s = s.replace('private val localLfm = LocalLfmTuNotEngine(appContext)', 'private val localQwen = LocalQwenTuNotEngine(appContext)')
s = s.replace('suspend fun localFallback(skipLfm: Boolean = false): String {', 'suspend fun localFallback(skipQwen: Boolean = false): String {')
s = s.replace('val lfmEligible = !mapRequest && !flashcardRequest && !quizRequest', 'val qwenEligible = !mapRequest && !flashcardRequest && !quizRequest')
s = s.replace('if (!skipLfm && lfmEligible && localLfm.isAvailable()) {', 'if (!skipQwen && qwenEligible && localQwen.isAvailable()) {')
s = s.replace('val answer = localLfm.answer(', 'val answer = localQwen.answer(')
s = s.replace('preferences.lastLfmError = ""', 'preferences.lastLocalAiError = ""')
s = s.replace('return markEngine("LFM2.5 local", answer)', 'return markEngine("Qwen2.5 local", answer)')
s = s.replace('preferences.lastLfmError = t.message ?: t.javaClass.simpleName', 'preferences.lastLocalAiError = t.message ?: t.javaClass.simpleName')
s = s.replace('"LFM2.5 local" -> return localFallback(skipLfm = false)', '"Qwen2.5 local" -> return localFallback(skipQwen = false)')
s = s.replace('"Local básico" -> return localFallback(skipLfm = true)', '"Local básico" -> return localFallback(skipQwen = true)')
p.write_text(s)

# -----------------------------------------------------------------------------
# Settings UI: Qwen selection/download + explicit legacy LFM cleanup.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt')
s = p.read_text()
s = s.replace(
'''    val studyProgress = remember(refreshTick) {
        runCatching { studyManager.progressPercent() }.getOrNull()
    }
''',
'''    val studyProgress = remember(refreshTick) {
        runCatching { studyManager.progressPercent() }.getOrNull()
    }
    val legacyLfmInstalled = remember(refreshTick) {
        runCatching { studyManager.hasLegacyLfmModel() }.getOrDefault(false)
    }
''')
s = s.replace('listOf("LFM2.5 local", "Local básico")', 'listOf("Qwen2.5 local", "Local básico")')
s = s.replace('if (aiEngine == "LFM2.5 local" && studyState != StudyModelState.INSTALLED) {\n                    Text("LFM2.5 todavía no está instalado.",', 'if (aiEngine == "Qwen2.5 local" && studyState != StudyModelState.INSTALLED) {\n                    Text("Qwen2.5 todavía no está instalado.",')
s = s.replace('if (preferences.lastLfmError.isNotBlank()) {\n                    Text("Último fallo de LFM2.5: ${preferences.lastLfmError}",', 'if (preferences.lastLocalAiError.isNotBlank()) {\n                    Text("Último fallo del modelo local: ${preferences.lastLocalAiError}",')
s = s.replace('subtitle = "${StudyModelSpec.MODEL_NAME} · ~731 MB · ${StudyModelSpec.LICENSE} · sin costo por tokens",', 'subtitle = "${StudyModelSpec.MODEL_NAME} · ~1.12 GB · ${StudyModelSpec.LICENSE} · sin costo por tokens",')
s = s.replace('saveMessage = "LFM2.5: ${it.message ?: "no se pudo iniciar la descarga"}"', 'saveMessage = "Qwen2.5: ${it.message ?: "no se pudo iniciar la descarga"}"')
needle = '''                DownloadComponentCard(
                    title = StudyModelSpec.DISPLAY_NAME,
                    subtitle = "${StudyModelSpec.MODEL_NAME} · ~1.12 GB · ${StudyModelSpec.LICENSE} · sin costo por tokens",
                    stateText = when (studyState) {
                        StudyModelState.INSTALLED -> "Instalado · listo para TuNot"
                        StudyModelState.DOWNLOADING -> "Descargando"
                        StudyModelState.NOT_INSTALLED -> "No instalado"
                    },
                    progress = studyProgress,
                    installed = studyState == StudyModelState.INSTALLED,
                    downloading = studyState == StudyModelState.DOWNLOADING,
                    onDownload = {
                        runCatching { studyManager.enqueueDownload() }
                            .onFailure { saveMessage = "Qwen2.5: ${it.message ?: "no se pudo iniciar la descarga"}" }
                        refreshTick++
                    },
                    onRemove = { runCatching { studyManager.removeModel() }; refreshTick++ }
                )
'''
legacy = needle + '''
                if (legacyLfmInstalled) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("LFM2.5 anterior", color = NotCanOffWhite, fontWeight = FontWeight.Medium)
                                Text("~731 MB · se conserva hasta que confirmes Qwen2.5", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedButton(onClick = {
                                runCatching { studyManager.removeLegacyLfmModel() }
                                    .onSuccess { saveMessage = "LFM2.5 anterior eliminado." }
                                    .onFailure { saveMessage = it.message ?: "No se pudo eliminar LFM2.5" }
                                refreshTick++
                            }) { Text("Eliminar") }
                        }
                    }
                }
'''
if needle not in s:
    raise SystemExit('Qwen download card insertion point missing')
s = s.replace(needle, legacy)
p.write_text(s)

# -----------------------------------------------------------------------------
# Chat badge highlights Qwen as a real generative engine.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt')
s = p.read_text().replace('engineLabel.contains("Mistral") || engineLabel.contains("LFM2.5")', 'engineLabel.contains("Mistral") || engineLabel.contains("Qwen2.5")')
p.write_text(s)

# -----------------------------------------------------------------------------
# Version bump. Release metadata is updated on main only after this branch builds.
# -----------------------------------------------------------------------------
p = Path('app/build.gradle.kts')
s = p.read_text()
s = s.replace('versionCode = 41', 'versionCode = 42')
s = s.replace('versionName = "0.8.18.2"', 'versionName = "0.8.19"')
p.write_text(s)

print('Qwen2.5 local TuNot patch applied.')
