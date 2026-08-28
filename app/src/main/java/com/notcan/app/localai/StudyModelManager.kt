package com.notcan.app.localai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

object StudyModelSpec {
    const val DISPLAY_NAME = "NotCan AI · Qwen3 0.6B"
    const val MODEL_NAME = "Qwen3 0.6B Q8_0"
    const val FILE_NAME = "Qwen3-0.6B-Q8_0.gguf"
    const val DOWNLOAD_URL = "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q8_0.gguf?download=true"
    const val APPROX_BYTES = 639_000_000L
    const val MIN_VALID_BYTES = 600_000_000L
    const val LICENSE = "Apache-2.0"

    // Kept only so v0.7.5 can clean up the previous local model after Qwen is installed.
    const val LEGACY_FILE_NAME = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"
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

    private fun legacyModelFile(): File = File(modelFile().parentFile, StudyModelSpec.LEGACY_FILE_NAME)

    fun state(): StudyModelState {
        val file = modelFile()
        if (isValidModel(file)) {
            // The previous 1.5B model is no longer needed and would otherwise waste ~1.1 GB.
            runCatching { legacyModelFile().delete() }
            return StudyModelState.INSTALLED
        }

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
            .setTitle("NotCan · IA local")
            .setDescription("Qwen3 0.6B Q8_0 · aprox. 639 MB · funciona sin internet")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(false)
            .setDestinationInExternalFilesDir(context, "models", StudyModelSpec.FILE_NAME)
        return manager.enqueue(request).also { id -> prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply() }
    }

    /**
     * Reuses a GGUF that already exists on the device. This is intentionally a copy into
     * NotCan's local model directory because llama.cpp needs a real filesystem path rather
     * than an Android content:// URI. No network transfer is performed.
     */
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
                throw IllegalArgumentException(
                    "El archivo no parece ser Qwen3 0.6B Q8_0 en formato GGUF o está incompleto"
                )
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
            if (downloadId > 0L) {
                runCatching {
                    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                    manager.remove(downloadId)
                }
            }
            prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
            runCatching { legacyModelFile().delete() }
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
        if (id > 0L) {
            runCatching {
                val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.remove(id)
            }
        }
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        val currentDeleted = !modelFile().exists() || modelFile().delete()
        val legacyDeleted = !legacyModelFile().exists() || legacyModelFile().delete()
        return currentDeleted && legacyDeleted
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

    companion object {
        private const val KEY_DOWNLOAD_ID = "download_id"
    }
}
