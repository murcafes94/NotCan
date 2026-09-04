package com.notcan.app.storage

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
