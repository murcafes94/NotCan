package com.notcan.app.localai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

/** Gemma 4 E2B package prepared by the LiteRT community for LiteRT-LM on Android. */
object GemmaLiteRtModelSpec {
    const val DISPLAY_NAME = "TuNot local avanzado · Gemma 4"
    const val MODEL_NAME = "Gemma 4 E2B Instruct · LiteRT-LM"
    const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val DOWNLOAD_URL = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
    const val APPROX_BYTES = 2_600_000_000L
    const val MIN_VALID_BYTES = 2_200_000_000L
    const val LICENSE = "Gemma"
}

enum class GemmaLiteRtModelState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED
}

class GemmaLiteRtModelManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("notcan_gemma_litert_model", Context.MODE_PRIVATE)

    fun modelFile(): File {
        val dir = appContext.getExternalFilesDir("models") ?: File(appContext.filesDir, "models")
        dir.mkdirs()
        return File(dir, GemmaLiteRtModelSpec.FILE_NAME)
    }

    fun state(): GemmaLiteRtModelState {
        if (isValidModel(modelFile())) return GemmaLiteRtModelState.INSTALLED

        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0L) {
            val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    if (
                        status == DownloadManager.STATUS_RUNNING ||
                        status == DownloadManager.STATUS_PENDING ||
                        status == DownloadManager.STATUS_PAUSED
                    ) return GemmaLiteRtModelState.DOWNLOADING
                }
            }
        }
        return GemmaLiteRtModelState.NOT_INSTALLED
    }

    fun enqueueDownload(): Long {
        if (state() == GemmaLiteRtModelState.INSTALLED) return -1L
        val existingId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (existingId > 0L && state() == GemmaLiteRtModelState.DOWNLOADING) return existingId

        val destination = modelFile()
        if (destination.exists()) destination.delete()

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(GemmaLiteRtModelSpec.DOWNLOAD_URL))
            .setTitle("NotCan · Gemma 4 local")
            .setDescription("Gemma 4 E2B · LiteRT-LM · aprox. 2.6 GB · funciona sin Internet")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(true)
            .setDestinationInExternalFilesDir(appContext, "models", GemmaLiteRtModelSpec.FILE_NAME)

        return manager.enqueue(request).also { id ->
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
        }
    }

    fun progressPercent(): Int? {
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id <= 0L) return null
        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
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
            (appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id)
        }
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        val file = modelFile()
        return !file.exists() || file.delete()
    }

    private fun isValidModel(file: File): Boolean =
        file.exists() && file.isFile && file.length() >= GemmaLiteRtModelSpec.MIN_VALID_BYTES

    companion object {
        private const val KEY_DOWNLOAD_ID = "download_id"
    }
}
