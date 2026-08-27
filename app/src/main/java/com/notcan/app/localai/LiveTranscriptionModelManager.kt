package com.notcan.app.localai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File

object LiveTranscriptionModelSpec {
    const val DISPLAY_NAME = "Moonshine base-es"
    const val ARCHIVE_NAME = "sherpa-onnx-moonshine-base-es-quantized-2026-02-27.tar.bz2"
    const val DOWNLOAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$ARCHIVE_NAME"
    const val MODEL_DIR = "moonshine-es"
    const val ENCODER = "encoder_model.ort"
    const val DECODER = "decoder_model_merged.ort"
    const val TOKENS = "tokens.txt"
    const val APPROX_BYTES = 63_000_000L
}

enum class LiveTranscriptionModelState { NOT_INSTALLED, DOWNLOADING, INSTALLED }

class LiveTranscriptionModelManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("notcan_live_asr", Context.MODE_PRIVATE)

    fun modelDir(): File {
        val root = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        return File(root, LiveTranscriptionModelSpec.MODEL_DIR).apply { mkdirs() }
    }

    fun encoderFile() = File(modelDir(), LiveTranscriptionModelSpec.ENCODER)
    fun decoderFile() = File(modelDir(), LiveTranscriptionModelSpec.DECODER)
    fun tokensFile() = File(modelDir(), LiveTranscriptionModelSpec.TOKENS)

    private fun archiveFile(): File {
        val root = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        root.mkdirs()
        return File(root, LiveTranscriptionModelSpec.ARCHIVE_NAME)
    }

    fun state(): LiveTranscriptionModelState {
        if (isInstalled()) return LiveTranscriptionModelState.INSTALLED
        val id = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (id > 0L) {
            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (cursor.moveToFirst()) {
                    when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_PAUSED -> return LiveTranscriptionModelState.DOWNLOADING
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            if (extractDownloadedArchive()) return LiveTranscriptionModelState.INSTALLED
                        }
                    }
                }
            }
        }
        return LiveTranscriptionModelState.NOT_INSTALLED
    }

    fun enqueueDownload(): Long {
        if (state() == LiveTranscriptionModelState.INSTALLED) return -1L
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val request = DownloadManager.Request(Uri.parse(LiveTranscriptionModelSpec.DOWNLOAD_URL))
            .setTitle("NotCan · transcripción en vivo")
            .setDescription("Moonshine español · ~63 MB · modelo local")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverRoaming(false)
            .setAllowedOverMetered(false)
            .setDestinationInExternalFilesDir(context, "models", LiveTranscriptionModelSpec.ARCHIVE_NAME)
        return manager.enqueue(request).also { prefs.edit().putLong(KEY_DOWNLOAD_ID, it).apply() }
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
        if (id > 0L) runCatching { (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id) }
        prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
        archiveFile().delete()
        return modelDir().deleteRecursively()
    }

    private fun isInstalled(): Boolean =
        encoderFile().length() > 15_000_000L &&
            decoderFile().length() > 30_000_000L &&
            tokensFile().length() > 100_000L

    private fun extractDownloadedArchive(): Boolean {
        val archive = archiveFile()
        if (!archive.exists() || archive.length() < 40_000_000L) return false
        if (isInstalled()) return true

        val target = modelDir().canonicalFile
        try {
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(archive.inputStream()))).use { tar ->
                while (true) {
                    val entry = tar.nextEntry ?: break
                    if (!entry.isFile) continue
                    val name = entry.name.substringAfterLast('/')
                    if (name !in setOf(LiveTranscriptionModelSpec.ENCODER, LiveTranscriptionModelSpec.DECODER, LiveTranscriptionModelSpec.TOKENS)) continue
                    val out = File(target, name).canonicalFile
                    require(out.path.startsWith(target.path + File.separator)) { "Ruta inválida en el modelo de voz" }
                    out.outputStream().buffered().use { output -> tar.copyTo(output) }
                }
            }
            if (isInstalled()) archive.delete()
        } catch (_: Throwable) {
            encoderFile().delete(); decoderFile().delete(); tokensFile().delete()
            return false
        }
        return isInstalled()
    }

    companion object { private const val KEY_DOWNLOAD_ID = "download_id" }
}
