package com.notcan.app.ui.home

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.WebResourceResponse
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Pequeña biblioteca de tipografías del usuario.
 *
 * Las fuentes se copian una sola vez al almacenamiento privado de NotCan para que
 * sigan disponibles sin conexión aunque el proveedor SAF/Drive deje de estar montado.
 * No se incluyen tipografías pesadas en el APK: el usuario decide cuáles importar.
 */
internal object LocalFontStore {

    data class Entry(
        val id: String,
        val displayName: String,
        val cssFamily: String,
        val fileName: String
    )

    private const val PREFS = "notcan_local_fonts"
    private const val IDS = "ids"
    private const val MAX_FONT_BYTES = 16L * 1024L * 1024L
    private const val HOST = "notcan.local"
    private const val PATH_PREFIX = "/font/"

    private fun directory(context: Context): File =
        File(context.filesDir, "typography/fonts").apply { mkdirs() }

    fun list(context: Context): List<Entry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(IDS, emptySet()).orEmpty().toMutableSet()
        var changed = false
        val result = ids.mapNotNull { id ->
            val fileName = prefs.getString("file_$id", null)
            val displayName = prefs.getString("name_$id", null)
            if (fileName.isNullOrBlank() || displayName.isNullOrBlank()) {
                changed = true
                null
            } else {
                val file = File(directory(context), fileName)
                if (!file.isFile) {
                    changed = true
                    null
                } else {
                    Entry(id, displayName, cssFamily(id), fileName)
                }
            }
        }.sortedBy { it.displayName.lowercase(Locale.ROOT) }
        if (changed) {
            val surviving = result.mapTo(linkedSetOf()) { it.id }
            prefs.edit().putStringSet(IDS, surviving).apply()
        }
        return result
    }

    fun importFont(context: Context, uri: Uri): Entry {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(context, uri).ifBlank { "Fuente local" }
        val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT)
            .takeIf { it == "ttf" || it == "otf" }
            ?: "font"
        val temp = File(directory(context), ".import-${System.nanoTime()}.$extension")
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        resolver.openInputStream(uri)?.use { input ->
            temp.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    total += read
                    require(total <= MAX_FONT_BYTES) { "La fuente supera el límite de 16 MB" }
                    digest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                }
            }
        } ?: error("No se pudo abrir la fuente")

        try {
            require(total > 0L) { "El archivo de fuente está vacío" }
            // Typeface valida que el archivo sea una tipografía utilizable por Android.
            Typeface.createFromFile(temp)
            val id = digest.digest().joinToString("") { "%02x".format(it) }.take(20)
            val finalFile = File(directory(context), "$id.$extension")
            if (!finalFile.exists()) {
                if (!temp.renameTo(finalFile)) {
                    temp.copyTo(finalFile, overwrite = true)
                    temp.delete()
                }
            } else {
                temp.delete()
            }
            val cleanName = displayName.substringBeforeLast('.', displayName)
                .replace(Regex("[\\t\\r\\n]"), " ")
                .trim()
                .take(80)
                .ifBlank { "Fuente local" }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ids = prefs.getStringSet(IDS, emptySet()).orEmpty().toMutableSet().apply { add(id) }
            prefs.edit()
                .putStringSet(IDS, ids)
                .putString("name_$id", cleanName)
                .putString("file_$id", finalFile.name)
                .apply()
            return Entry(id, cleanName, cssFamily(id), finalFile.name)
        } catch (t: Throwable) {
            temp.delete()
            throw t
        }
    }

    fun remove(context: Context, id: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString("file_$id", null)?.let { File(directory(context), it).delete() }
        val ids = prefs.getStringSet(IDS, emptySet()).orEmpty().toMutableSet().apply { remove(id) }
        prefs.edit()
            .putStringSet(IDS, ids)
            .remove("name_$id")
            .remove("file_$id")
            .apply()
    }

    fun removeAll(context: Context) {
        list(context).forEach { File(directory(context), it.fileName).delete() }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun fontFaceCss(context: Context): String = list(context).joinToString("\n") { entry ->
        val format = if (entry.fileName.endsWith(".otf", true)) "opentype" else "truetype"
        "@font-face{font-family:'${entry.cssFamily}';src:url('https://$HOST/font/${entry.id}') format('$format');font-style:normal;}"
    }

    fun intercept(context: Context, uri: Uri?): WebResourceResponse? {
        if (uri == null || uri.scheme != "https" || uri.host != HOST || !uri.path.orEmpty().startsWith(PATH_PREFIX)) return null
        val id = uri.path.orEmpty().removePrefix(PATH_PREFIX).substringBefore('/')
        val entry = list(context).firstOrNull { it.id == id } ?: return null
        val file = File(directory(context), entry.fileName)
        if (!file.isFile) return null
        val mime = if (entry.fileName.endsWith(".otf", true)) "font/otf" else "font/ttf"
        return WebResourceResponse(mime, null, file.inputStream())
    }

    fun resolveTypeface(context: Context, family: String?): Typeface? {
        val clean = firstFamily(family ?: return null)
        val entry = list(context).firstOrNull { it.cssFamily.equals(clean, true) } ?: return null
        return runCatching { Typeface.createFromFile(File(directory(context), entry.fileName)) }.getOrNull()
    }

    fun exportName(context: Context, family: String?): String? {
        val clean = firstFamily(family ?: return null)
        return list(context).firstOrNull { it.cssFamily.equals(clean, true) }?.displayName
    }

    private fun cssFamily(id: String): String = "NotCanLocal_$id"

    private fun firstFamily(value: String): String = value.substringBefore(',')
        .trim()
        .removeSurrounding("\"")
        .removeSurrounding("'")

    private fun queryDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }
}
