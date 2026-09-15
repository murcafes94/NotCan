package com.notcan.app.localai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import java.io.File

/** Download/install state for an experimental MiniCPM LiteRT-LM bundle. */
enum class MiniCpmModelState {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED
}

/**
 * Download manager for MiniCPM5 experimental bundles.
 *
 * This class deliberately does not initialize LiteRT-LM. The current stable NotCan runtime remains
 * pinned to 0.11.0 while MiniCPM5 requires 0.17.0 according to the upstream deployment guide.
 * Keeping download/install lifecycle separate lets the experimental branch validate files and UX
 * before changing the inference runtime used by Gemma 4.
 */
class MiniCpmModelManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun modelFile(id: MiniCpmModelId): File {
        val root = appContext.getExternalFilesDir("models") ?: File(appContext.filesDir, "models")
        val dir = File(root, "minicpm").apply { mkdirs() }
        return File(dir, MiniCpmLiteRtCatalog.spec(id).fileName)
    }

    fun state(id: MiniCpmModelId): MiniCpmModelState {
        val spec = MiniCpmLiteRtCatalog.spec(id)
        if (isValidModel(modelFile(id), spec)) return MiniCpmModelState.INSTALLED

        val downloadId = prefs.getLong(downloadKey(id), -1L)
        if (downloadId <= 0L) return MiniCpmModelState.NOT_INSTALLED

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use MiniCpmModelState.NOT_INSTALLED
                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PAUSED -> MiniCpmModelState.DOWNLOADING
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        if (isValidModel(modelFile(id), spec)) MiniCpmModelState.INSTALLED
                        else MiniCpmModelState.NOT_INSTALLED
                    }
                    else -> MiniCpmModelState.NOT_INSTALLED
                }
            } ?: MiniCpmModelState.NOT_INSTALLED
        }.getOrDefault(MiniCpmModelState.NOT_INSTALLED)
    }

    fun enqueueDownload(id: MiniCpmModelId): Long {
        val spec = MiniCpmLiteRtCatalog.spec(id)
        if (state(id) == MiniCpmModelState.INSTALLED) return -1L

        val existing = prefs.getLong(downloadKey(id), -1L)
        if (existing > 0L && state(id) == MiniCpmModelState.DOWNLOADING) return existing

        val destination = modelFile(id)
        if (destination.exists()) destination.delete()

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(spec.downloadUrl))
            .setTitle("NotCan · ${spec.displayName}")
            .setDescription("Modelo experimental MiniCPM para TuNot · descarga local")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(false)
            .setDestinationInExternalFilesDir(
                appContext,
                "models/minicpm",
                spec.fileName
            )

        return manager.enqueue(request).also { downloadId ->
            prefs.edit().putLong(downloadKey(id), downloadId).apply()
        }
    }

    fun progressPercent(id: MiniCpmModelId): Int? {
        val downloadId = prefs.getLong(downloadKey(id), -1L)
        if (downloadId <= 0L) return null

        val manager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(downloadId))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                if (downloaded < 0L || total <= 0L) return@use null
                ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            }
        }.getOrNull()
    }

    fun removeModel(id: MiniCpmModelId): Boolean {
        val downloadId = prefs.getLong(downloadKey(id), -1L)
        if (downloadId > 0L) {
            runCatching {
                (appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(downloadId)
            }
        }
        prefs.edit().remove(downloadKey(id)).apply()
        val file = modelFile(id)
        return !file.exists() || file.delete()
    }

    fun installedModels(): List<MiniCpmModelId> =
        MiniCpmLiteRtCatalog.models.map { it.id }.filter { state(it) == MiniCpmModelState.INSTALLED }

    private fun isValidModel(file: File, spec: MiniCpmLiteRtModelSpec): Boolean =
        file.exists() && file.isFile && file.length() >= spec.minimumValidBytes

    private fun downloadKey(id: MiniCpmModelId): String = "download_${id.name.lowercase()}"

    companion object {
        private const val PREFS_NAME = "notcan_minicpm_models"
    }
}
