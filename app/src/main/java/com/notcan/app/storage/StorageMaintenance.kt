package com.notcan.app.storage

import android.app.DownloadManager
import android.content.Context
import java.io.File

object StorageMaintenance {
    data class CleanupResult(
        val filesRemoved: Int,
        val bytesFreed: Long
    ) {
        operator fun plus(other: CleanupResult) = CleanupResult(
            filesRemoved = filesRemoved + other.filesRemoved,
            bytesFreed = bytesFreed + other.bytesFreed
        )
    }

    data class CacheSnapshot(
        val bytes: Long,
        val files: Int
    )

    private val obsoleteNames = setOf(
        "qwen2.5-1.5b-instruct-q4_k_m.gguf",
        "Qwen3-0.6B-Q8_0.gguf",
        "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
        "LFM2.5-1.2B-Instruct-Q4_K_M.gguf"
    )

    private const val STARTUP_INTERVAL_MS = 12L * 60L * 60L * 1_000L
    private const val STARTUP_MIN_AGE_MS = 12L * 60L * 60L * 1_000L
    private const val MANUAL_MIN_AGE_MS = 2L * 60L * 60L * 1_000L
    private const val STARTUP_CACHE_TARGET_BYTES = 256L * 1024L * 1024L
    private const val PREFS = "notcan_storage_maintenance"
    private const val KEY_LAST_STARTUP = "last_startup_cleanup_ms"

    /**
     * Mantenimiento seguro y amortizado. Nunca toca modelos vigentes, audios, documentos,
     * bases de datos ni archivos del usuario: solo modelos obsoletos y cacheDir.
     */
    fun runStartupMaintenance(context: Context): CleanupResult {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val last = prefs.getLong(KEY_LAST_STARTUP, 0L)
        if (last > 0L && now - last < STARTUP_INTERVAL_MS) return CleanupResult(0, 0L)

        val result = cleanupObsoleteAi(app) + cleanupTransientCache(app, aggressive = false)
        prefs.edit().putLong(KEY_LAST_STARTUP, now).apply()
        return result
    }

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

    /**
     * Limpia únicamente archivos reconstruibles de [Context.getCacheDir].
     *
     * En automático conserva todo lo reciente y solo reduce una caché grande hacia 256 MiB.
     * En limpieza manual elimina temporales antiguos (>= 2 h), pero sigue protegiendo archivos
     * que podrían estar siendo usados por Whisper, Groq, TuNot o el visor en ese momento.
     */
    fun cleanupTransientCache(context: Context, aggressive: Boolean): CleanupResult {
        val root = context.applicationContext.cacheDir
        if (!root.exists()) return CleanupResult(0, 0L)

        val now = System.currentTimeMillis()
        val minAge = if (aggressive) MANUAL_MIN_AGE_MS else STARTUP_MIN_AGE_MS
        val cutoff = now - minAge
        val files = collectFiles(root)
        if (files.isEmpty()) return CleanupResult(0, 0L)

        var totalBytes = files.sumOf { safeLength(it) }
        val candidates = files
            .asSequence()
            .filter { safeLastModified(it) in 1..cutoff }
            .sortedBy { safeLastModified(it) }
            .toList()

        var removed = 0
        var freed = 0L
        val shouldTrimToTarget = !aggressive && totalBytes > STARTUP_CACHE_TARGET_BYTES

        for (file in candidates) {
            val knownTransient = isKnownTransient(file)
            val shouldDelete = aggressive || knownTransient || shouldTrimToTarget && totalBytes > STARTUP_CACHE_TARGET_BYTES
            if (!shouldDelete) continue
            val size = safeLength(file)
            if (runCatching { file.delete() }.getOrDefault(false)) {
                removed++
                freed += size
                totalBytes = (totalBytes - size).coerceAtLeast(0L)
            }
            if (!aggressive && totalBytes <= STARTUP_CACHE_TARGET_BYTES && !knownTransient) break
        }

        pruneEmptyDirectories(root)
        return CleanupResult(removed, freed)
    }

    fun cacheSnapshot(context: Context): CacheSnapshot {
        val files = collectFiles(context.applicationContext.cacheDir)
        return CacheSnapshot(
            bytes = files.sumOf { safeLength(it) },
            files = files.size
        )
    }

    private fun collectFiles(root: File): List<File> {
        if (!root.exists()) return emptyList()
        val result = ArrayList<File>()
        val pending = ArrayDeque<File>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val current = pending.removeLast()
            val children = runCatching { current.listFiles().orEmpty() }.getOrDefault(emptyArray())
            children.forEach { child ->
                if (child.isDirectory) pending.add(child)
                else if (child.isFile) result.add(child)
            }
        }
        return result
    }

    private fun isKnownTransient(file: File): Boolean {
        val name = file.name.lowercase()
        val parent = file.parentFile?.name?.lowercase().orEmpty()
        return name.endsWith(".tmp") ||
            name.endsWith(".part") ||
            name.endsWith(".pcm") ||
            name.endsWith(".wav") ||
            name.startsWith("tunot_source_") ||
            parent == "groq_chunks" ||
            parent == "whisper"
    }

    private fun pruneEmptyDirectories(root: File) {
        runCatching {
            root.walkBottomUp()
                .filter { it.isDirectory && it != root }
                .forEach { dir -> if (dir.listFiles().isNullOrEmpty()) dir.delete() }
        }
    }

    private fun safeLength(file: File): Long = runCatching { file.length() }.getOrDefault(0L).coerceAtLeast(0L)
    private fun safeLastModified(file: File): Long = runCatching { file.lastModified() }.getOrDefault(0L)
}
