package com.notcan.app.localai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

object StudyModelSpec {
    const val DISPLAY_NAME = "NotCan AI · DeepSeek R1 1.5B"
    const val MODEL_NAME = "DeepSeek-R1-Distill-Qwen-1.5B Q4_K_M"
    const val FILE_NAME = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf"
    const val DOWNLOAD_URL = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf?download=true"
    const val APPROX_BYTES = 1_120_000_000L
    const val MIN_VALID_BYTES = 1_000_000_000L
    const val SHA256 = "1741e5b2d062b07acf048bf0d2c514dadf2a48f94e2b4aa0cfe069af3838ee2f"
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

    fun state(): StudyModelState {
        val file = modelFile()
        if (file.exists() && file.length() >= StudyModelSpec.MIN_VALID_BYTES) return StudyModelState.INSTALLED

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
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(StudyModelSpec.DOWNLOAD_URL))
            .setTitle("NotCan · IA local")
            .setDescription("DeepSeek R1 1.5B · aprox. 1,1 GB · sin tokens")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(false)
            .setDestinationInExternalFilesDir(context, "models", StudyModelSpec.FILE_NAME)
        return manager.enqueue(request).also { id -> prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply() }
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
        val file = modelFile()
        return !file.exists() || file.delete()
    }

    companion object {
        private const val KEY_DOWNLOAD_ID = "download_id"
    }
}
