package com.notcan.app.ai.harness

/**
 * Lightweight, on-device privacy guard used before sending prompts to remote providers.
 * It intentionally targets high-confidence patterns so academic content is not over-redacted.
 */
object TuNotPrivacyGuard {
    private val email = Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")
    private val phone = Regex("(?<!\\d)(?:\\+?\\d[\\d .()_-]{7,}\\d)(?!\\d)")
    private val longIdentifier = Regex("(?<!\\d)\\d{10,18}(?!\\d)")

    fun sanitizeForRemote(text: String): String = text
        .replace(email, "[EMAIL]")
        .replace(phone, "[TELÉFONO]")
        .replace(longIdentifier, "[IDENTIFICADOR]")
}

/**
 * Prevents a remote model from surfacing URLs that were not actually retrieved by NotCan.
 * This is a conservative citation-grounding guard, not a semantic fact checker.
 */
object TuNotCitationGuard {
    private val urlRegex = Regex("https?://[^\\s)\\]}>]+", RegexOption.IGNORE_CASE)

    fun enforceRetrievedUrls(response: String, allowedUrls: Set<String>): String {
        if (response.isBlank()) return response
        val normalizedAllowed = allowedUrls.map(::normalizeUrl).toSet()
        return urlRegex.replace(response) { match ->
            val raw = match.value.trimEnd('.', ',', ';', ':')
            if (normalizeUrl(raw) in normalizedAllowed) match.value else "[enlace no verificado]"
        }
    }

    private fun normalizeUrl(value: String): String = value
        .trim()
        .trimEnd('/', '.', ',', ';', ':')
        .lowercase()
}

data class TuNotQualitySnapshot(
    val score: Int,
    val flags: List<String>
)

/**
 * Zero-cost heuristic evaluator for diagnostics and benchmarks. It does not call another LLM and is
 * deliberately not used to rewrite every normal response. The scoring surface can later be replaced
 * by pairwise Arena-style evaluation without changing the chat path.
 */
object TuNotQualityEvaluator {
    fun evaluate(
        question: String,
        response: String,
        strictSources: Boolean,
        usedWeb: Boolean,
        allowedUrls: Set<String> = emptySet()
    ): TuNotQualitySnapshot {
        val flags = mutableListOf<String>()
        var score = 100
        val clean = response.trim()

        if (clean.length < 30) {
            score -= 30
            flags += "respuesta_demasiado_corta"
        }
        if (clean.contains("no consta en las fuentes disponibles", ignoreCase = true) && !strictSources) {
            score -= 10
            flags += "rechazo_fuera_de_modo_fuentes"
        }
        if (strictSources && clean.contains("según mi conocimiento", ignoreCase = true)) {
            score -= 25
            flags += "posible_conocimiento_externo"
        }
        if (usedWeb) {
            val urls = Regex("https?://[^\\s)\\]}>]+", RegexOption.IGNORE_CASE)
                .findAll(clean)
                .map { it.value.trimEnd('.', ',', ';', ':').lowercase() }
                .toList()
            val allowed = allowedUrls.map { it.trimEnd('/').lowercase() }.toSet()
            if (urls.any { it.trimEnd('/') !in allowed }) {
                score -= 25
                flags += "url_no_recuperada"
            }
        }
        if (question.length < 120 && clean.length > 6000) {
            score -= 10
            flags += "desproporcion_longitud"
        }

        return TuNotQualitySnapshot(score.coerceIn(0, 100), flags)
    }
}
