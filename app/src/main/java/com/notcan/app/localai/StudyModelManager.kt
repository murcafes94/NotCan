package com.notcan.app.localai

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
