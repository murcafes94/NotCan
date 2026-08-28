package com.notcan.app.localai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

object WhisperModelSpec {
    const val DISPLAY_NAME = "Whisper large-v3-turbo"
    const val FILE_NAME = "ggml-large-v3-turbo.bin"
    const val DOWNLOAD_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo.bin?download=true"
    const val APPROX_BYTES = 1_620_000_000L
    const val MIN_VALID_BYTES = 1_400_000_000L
}

enum class WhisperModelState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED
}

class WhisperModelManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("notcan_whisper", Context.MODE_PRIVATE)

    fun modelFile(): File {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        dir.mkdirs()
        return File(dir, WhisperModelSpec.FILE_NAME)
    }

    fun state(): WhisperModelState {
        val file = modelFile()
        if (file.exists() && file.length() >= WhisperModelSpec.MIN_VALID_BYTES) {
            return WhisperModelState.INSTALLED
        }
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0L) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING || status == DownloadManager.STATUS_PAUSED) {
                        return WhisperModelState.DOWNLOADING
                    }
                }
            }
        }
        return WhisperModelState.NOT_INSTALLED
    }

    fun enqueueDownload(): Long {
        if (state() == WhisperModelState.INSTALLED) return -1L
        val existingId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (existingId > 0L && state() == WhisperModelState.DOWNLOADING) return existingId

        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(WhisperModelSpec.DOWNLOAD_URL))
            .setTitle("NotCan · ${WhisperModelSpec.DISPLAY_NAME}")
            .setDescription("Modelo local de transcripción final · aproximadamente 1,5 GB")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(false)
            .setDestinationInExternalFilesDir(context, "models", WhisperModelSpec.FILE_NAME)
        val id = manager.enqueue(request)
        prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        return id
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
                (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
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
