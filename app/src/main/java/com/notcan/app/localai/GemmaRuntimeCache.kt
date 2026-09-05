package com.notcan.app.localai

import android.content.Context
import java.io.File

/**
 * LiteRT runtime artifacts are expensive to rebuild and are not transient user cache.
 * Keep them outside cacheDir so Android/NotCan cleanup does not erase them between sessions.
 * noBackupFilesDir avoids cloud backup of device-specific compiled artifacts.
 */
object GemmaRuntimeCache {
    private const val DIR_NAME = "litert_gemma4_runtime"

    fun directory(context: Context): File = File(context.applicationContext.noBackupFilesDir, DIR_NAME).apply {
        mkdirs()
    }

    fun sizeBytes(context: Context): Long = runCatching {
        directory(context).walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length().coerceAtLeast(0L) }
    }.getOrDefault(0L)

    fun fileCount(context: Context): Int = runCatching {
        directory(context).walkTopDown().count { it.isFile }
    }.getOrDefault(0)
}
