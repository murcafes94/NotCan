package com.notcan.app.ai

import android.content.Context
import android.text.Html
import com.notcan.app.BuildConfig
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Web research layer for TuNot.
 *
 * Search is intentionally independent from Mistral: NotCan first retrieves web results, then passes
 * them to the model as explicit sources. A Supabase/SearXNG backend is attempted when available and
 * DuckDuckGo HTML remains the zero-key fallback.
 */
class WebResearchService(context: Context) {
    private val appContext = context.applicationContext

    data class Result(
        val title: String,
        val url: String,
        val snippet: String,
        val engine: String = "duckduckgo",
        val pageText: String = ""
    )

    fun search(query: String, limit: Int = 6): List<Result> {
        val clean = query.trim().take(500)
        if (clean.isBlank()) return emptyList()
        return runCatching { searchViaSupabase(clean, limit) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: searchDuckDuckGo(clean, limit)
    }

    fun research(query: String, limit: Int = 5, readTop: Int = 3): List<Result> {
        val results = search(query, limit)
        return results.mapIndexed { index, item ->
            if (index >= readTop) item
            else item.copy(pageText = runCatching { readPage(item.url) }.getOrDefault(""))
        }
    }

    fun readPage(rawUrl: String, maxChars: Int = 70_000): String {
        val url = URL(rawUrl)
        require(url.protocol == "http" || url.protocol == "https") { "Solo se admiten páginas http/https" }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 12_000
            readTimeout = 18_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.6")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return ""
            val type = connection.contentType.orEmpty().lowercase()
            if (type.isNotBlank() && !type.contains("html") && !type.contains("text") && !type.contains("xml")) return ""
            val raw = BufferedInputStream(connection.inputStream).use { input ->
                val out = StringBuilder()
                val buffer = ByteArray(8_192)
                while (out.length < MAX_HTML_CHARS) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.append(String(buffer, 0, read, Charsets.UTF_8))
                }
                out.toString()
            }
            htmlToText(raw).take(maxChars)
        } finally {
            connection.disconnect()
        }
    }

    fun formatForPrompt(results: List<Result>, maxChars: Int = 24_000): String = buildString {
        results.forEachIndexed { index, item ->
            appendLine("\n=== FUENTE WEB ${index + 1} ===")
            appendLine("Título: ${item.title}")
            appendLine("URL: ${item.url}")
            if (item.snippet.isNotBlank()) appendLine("Resultado: ${item.snippet}")
            val body = item.pageText.ifBlank { item.snippet }
            if (body.isNotBlank()) {
                appendLine("Contenido recuperado:")
                appendLine(body.take(6_500))
            }
        }
    }.take(maxChars)

    private fun searchViaSupabase(query: String, limit: Int): List<Result> {
        val endpoint = "${BuildConfig.SUPABASE_URL.trimEnd('/')}/functions/v1/notcan-web-search"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                it.write(JSONObject().put("q", query).put("limit", limit.coerceIn(1, 8)).put("engine", "auto").toString())
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val root = JSONObject(connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val array = root.optJSONArray("results") ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val url = item.optString("url").trim()
                    if (!isHttpUrl(url)) continue
                    add(Result(
                        title = clean(item.optString("title"), 240).ifBlank { url },
                        url = url,
                        snippet = clean(item.optString("snippet"), 900),
                        engine = clean(item.optString("engine"), 40).ifBlank { "web" }
                    ))
                }
            }.take(limit)
        } finally {
            connection.disconnect()
        }
    }

    private fun searchDuckDuckGo(query: String, limit: Int): List<Result> {
        val body = "q=${URLEncoder.encode(query, "UTF-8")}&kl=es-es"
        val connection = (URL("https://html.duckduckgo.com/html/").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("User-Agent", MOBILE_UA)
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
            setRequestProperty("Accept-Language", "es-ES,es;q=0.9,en;q=0.6")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        return try {
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            if (connection.responseCode !in 200..299) return emptyList()
            val html = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parseDuckDuckGo(html, limit)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseDuckDuckGo(html: String, limit: Int): List<Result> {
        val resultRegex = Regex("<div[^>]+class=\"[^\"]*result[^\"]*\"[^>]*>(.*?)(?=<div[^>]+class=\"[^\"]*result[^\"]*\"|$)", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val linkRegex = Regex("<a[^>]+class=\"[^\"]*result__a[^\"]*\"[^>]+href=\"([^\"]+)\"[^>]*>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val snippetRegex = Regex("<(?:a|div)[^>]+class=\"[^\"]*result__snippet[^\"]*\"[^>]*>(.*?)</(?:a|div)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val out = mutableListOf<Result>()
        val seen = mutableSetOf<String>()
        for (block in resultRegex.findAll(html)) {
            val inner = block.groupValues[1]
            val link = linkRegex.find(inner) ?: continue
            val resolved = normalizeDuckUrl(link.groupValues[1])
            if (!isHttpUrl(resolved) || !seen.add(resolved)) continue
            out += Result(
                title = clean(link.groupValues[2], 240).ifBlank { resolved },
                url = resolved,
                snippet = clean(snippetRegex.find(inner)?.groupValues?.getOrNull(1).orEmpty(), 900),
                engine = "duckduckgo"
            )
            if (out.size >= limit) break
        }
        return out
    }

    private fun normalizeDuckUrl(raw: String): String {
        val decodedHtml = Html.fromHtml(raw.replace("&amp;", "&"), Html.FROM_HTML_MODE_LEGACY).toString()
        val absolute = if (decodedHtml.startsWith("//")) "https:$decodedHtml" else decodedHtml
        return runCatching {
            val parsed = URL(URL("https://duckduckgo.com/"), absolute)
            val query = parsed.query.orEmpty().split('&').associate {
                val parts = it.split('=', limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }
            val target = query["uddg"]?.let { URLDecoder.decode(it, "UTF-8") }
            target?.takeIf(::isHttpUrl) ?: parsed.toString()
        }.getOrDefault("")
    }

    private fun htmlToText(raw: String): String {
        val cleaned = raw
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("(?is)<noscript.*?>.*?</noscript>"), " ")
            .replace(Regex("(?is)<svg.*?>.*?</svg>"), " ")
            .replace(Regex("(?i)</?(p|div|section|article|main|header|footer|li|h[1-6]|br)[^>]*>"), "\n")
        return Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace('\u0000', ' ')
            .replace(Regex("[\t ]+"), " ")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    private fun clean(value: String, max: Int): String = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString()
        .replace(Regex("\\s+"), " ").trim().take(max)

    companion object {
        private const val MAX_HTML_CHARS = 650_000
        private const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36 NotCan/0.8"

        fun shouldAutoSearch(question: String): Boolean {
            val q = question.lowercase()
            return listOf(
                "busca", "buscar", "web", "internet", "en línea", "online", "fuente reciente",
                "hoy", "actualmente", "actual", "reciente", "última", "último", "noticia", "vigente",
                "quién es ahora", "qué pasó", "este año", "2026"
            ).any { it in q }
        }

        private fun isHttpUrl(value: String): Boolean = value.startsWith("https://") || value.startsWith("http://")
    }
}
