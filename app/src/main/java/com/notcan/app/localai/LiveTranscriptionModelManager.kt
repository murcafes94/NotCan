package com.notcan.app.localai

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File

object LiveTranscriptionModelSpec {
    const val DISPLAY_NAME = "Moonshine base-es + Silero VAD"
    const val ARCHIVE_NAME = "sherpa-onnx-moonshine-base-es-quantized-2026-02-27.tar.bz2"
    const val DOWNLOAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$ARCHIVE_NAME"
    const val MODEL_DIR = "moonshine-es"
    const val ENCODER = "encoder_model.ort"
    const val DECODER = "decoder_model_merged.ort"
    const val TOKENS = "tokens.txt"
    const val VAD = "silero_vad.onnx"
    const val VAD_DOWNLOAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$VAD"
    const val APPROX_BYTES = 64_000_000L
    const val MIN_VAD_BYTES = 200_000L
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
    fun vadFile() = File(modelDir(), LiveTranscriptionModelSpec.VAD)

    private fun archiveFile(): File {
        val root = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        root.mkdirs()
        return File(root, LiveTranscriptionModelSpec.ARCHIVE_NAME)
    }

    fun state(): LiveTranscriptionModelState {
        if (isInstalled()) return LiveTranscriptionModelState.INSTALLED

        var downloading = false
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        val asrId = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        if (!isAsrInstalled() && asrId > 0L) {
            queryStatus(manager, asrId)?.let { status ->
                when (status) {
                    DownloadManager.STATUS_RUNNING,
                    DownloadManager.STATUS_PENDING,
                    DownloadManager.STATUS_PAUSED -> downloading = true
                    DownloadManager.STATUS_SUCCESSFUL -> extractDownloadedArchive()
                }
            }
        }

        val vadId = prefs.getLong(KEY_VAD_DOWNLOAD_ID, -1L)
        if (!isVadInstalled() && vadId > 0L) {
            queryStatus(manager, vadId)?.let { status ->
                if (status == DownloadManager.STATUS_RUNNING ||
                    status == DownloadManager.STATUS_PENDING ||
                    status == DownloadManager.STATUS_PAUSED
                ) downloading = true
            }
        }

        if (isInstalled()) return LiveTranscriptionModelState.INSTALLED
        return if (downloading) LiveTranscriptionModelState.DOWNLOADING else LiveTranscriptionModelState.NOT_INSTALLED
    }

    fun enqueueDownload(): Long {
        if (isInstalled()) return -1L
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        var firstId = -1L

        if (!isAsrInstalled() && !isDownloadActive(manager, prefs.getLong(KEY_DOWNLOAD_ID, -1L))) {
            archiveFile().delete()
            val request = DownloadManager.Request(Uri.parse(LiveTranscriptionModelSpec.DOWNLOAD_URL))
                .setTitle("NotCan · transcripción en vivo")
                .setDescription("Moonshine español · ~63 MB · modelo local")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(false)
                .setAllowedOverMetered(false)
                .setDestinationInExternalFilesDir(context, "models", LiveTranscriptionModelSpec.ARCHIVE_NAME)
            val id = manager.enqueue(request)
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply()
            firstId = id
        }

        if (!isVadInstalled() && !isDownloadActive(manager, prefs.getLong(KEY_VAD_DOWNLOAD_ID, -1L))) {
            vadFile().delete()
            val request = DownloadManager.Request(Uri.parse(LiveTranscriptionModelSpec.VAD_DOWNLOAD_URL))
                .setTitle("NotCan · detección de voz")
                .setDescription("Silero VAD · menos de 1 MB · mejora los cortes de la transcripción")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setAllowedOverRoaming(false)
                .setAllowedOverMetered(false)
                .setDestinationInExternalFilesDir(context, "models/${LiveTranscriptionModelSpec.MODEL_DIR}", LiveTranscriptionModelSpec.VAD)
            val id = manager.enqueue(request)
            prefs.edit().putLong(KEY_VAD_DOWNLOAD_ID, id).apply()
            if (firstId <= 0L) firstId = id
        }

        return firstId
    }

    fun progressPercent(): Int? {
        if (isInstalled()) return 100
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val values = buildList {
            if (!isAsrInstalled()) downloadProgress(manager, prefs.getLong(KEY_DOWNLOAD_ID, -1L))?.let(::add)
            if (!isVadInstalled()) downloadProgress(manager, prefs.getLong(KEY_VAD_DOWNLOAD_ID, -1L))?.let(::add)
        }
        return values.takeIf { it.isNotEmpty() }?.average()?.toInt()?.coerceIn(0, 100)
    }

    fun removeModel(): Boolean {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        listOf(
            prefs.getLong(KEY_DOWNLOAD_ID, -1L),
            prefs.getLong(KEY_VAD_DOWNLOAD_ID, -1L)
        ).filter { it > 0L }.forEach { id -> runCatching { manager.remove(id) } }
        prefs.edit().remove(KEY_DOWNLOAD_ID).remove(KEY_VAD_DOWNLOAD_ID).apply()
        archiveFile().delete()
        return modelDir().deleteRecursively()
    }

    private fun isInstalled(): Boolean = isAsrInstalled() && isVadInstalled()

    private fun isAsrInstalled(): Boolean =
        encoderFile().length() > 15_000_000L &&
            decoderFile().length() > 30_000_000L &&
            tokensFile().length() > 100_000L

    private fun isVadInstalled(): Boolean = vadFile().length() >= LiveTranscriptionModelSpec.MIN_VAD_BYTES

    private fun queryStatus(manager: DownloadManager, id: Long): Int? {
        if (id <= 0L) return null
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            }
        }.getOrNull()
    }

    private fun isDownloadActive(manager: DownloadManager, id: Long): Boolean {
        val status = queryStatus(manager, id) ?: return false
        return status == DownloadManager.STATUS_RUNNING ||
            status == DownloadManager.STATUS_PENDING ||
            status == DownloadManager.STATUS_PAUSED
    }

    private fun downloadProgress(manager: DownloadManager, id: Long): Int? {
        if (id <= 0L) return null
        return runCatching {
            manager.query(DownloadManager.Query().setFilterById(id))?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                if (downloaded < 0L || total <= 0L) null
                else ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
            }
        }.getOrNull()
    }

    private fun extractDownloadedArchive(): Boolean {
        val archive = archiveFile()
        if (!archive.exists() || archive.length() < 40_000_000L) return false
        if (isAsrInstalled()) return true

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
            if (isAsrInstalled()) archive.delete()
        } catch (_: Throwable) {
            encoderFile().delete(); decoderFile().delete(); tokensFile().delete()
            return false
        }
        return isAsrInstalled()
    }

    companion object {
        private const val KEY_DOWNLOAD_ID = "download_id"
        private const val KEY_VAD_DOWNLOAD_ID = "vad_download_id"
    }
}
