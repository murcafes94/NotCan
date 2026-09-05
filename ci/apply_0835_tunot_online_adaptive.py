from pathlib import Path

root = Path(__file__).resolve().parents[1]

# 1) Internet-aware routing and explicit connectivity labels.
service = root / "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
s = service.read_text()

old = "import android.content.Context\nimport android.text.Html\n"
new = "import android.content.Context\nimport android.net.ConnectivityManager\nimport android.net.NetworkCapabilities\nimport android.text.Html\n"
assert old in s, "imports anchor not found"
s = s.replace(old, new, 1)

old = '''        val shouldWarm = preference == "Gemma 4 local" ||
            (preference == "Automático" && !isConfigured())'''
new = '''        val shouldWarm = preference == "Gemma 4 local" ||
            (preference == "Automático" && (!isConfigured() || !hasValidatedInternet()))'''
assert old in s, "warm-up anchor not found"
s = s.replace(old, new, 1)

old = '''        val plainNotes = sourcePlainText(notes)
        val plainTranscript = sourcePlainText(transcript)

        val wantsWeb = !strictSources && (forcedWeb || (autoWeb && WebResearchService.shouldAutoSearch(cleanQuestion)))'''
new = '''        val plainNotes = sourcePlainText(notes)
        val plainTranscript = sourcePlainText(transcript)
        val internetAvailable = hasValidatedInternet()

        val wantsWeb = internetAvailable && !strictSources && (forcedWeb || (autoWeb && WebResearchService.shouldAutoSearch(cleanQuestion)))'''
assert old in s, "internet/wantsWeb anchor not found"
s = s.replace(old, new, 1)

old = '''        suspend fun localFallback(allowGemma: Boolean = true): String {
            if (allowGemma && localGemma.isAvailable()) {'''
new = '''        suspend fun localFallback(allowGemma: Boolean = true): String {
            val connectivityLabel = if (internetAvailable) "online" else "offline"
            val webUsageSuffix = if (webContext.isNotBlank()) " · web" else ""
            if (allowGemma && localGemma.isAvailable()) {'''
assert old in s, "localFallback anchor not found"
s = s.replace(old, new, 1)

s = s.replace('markEngine("Gemma 4 local · $backendLabel", partialText)', 'markEngine("Gemma 4 local · $backendLabel · $connectivityLabel$webUsageSuffix", partialText)')
s = s.replace('return markEngine("Gemma 4 local · ${answer.backendLabel}", answer.text)', 'return markEngine("Gemma 4 local · ${answer.backendLabel} · $connectivityLabel$webUsageSuffix", answer.text)')
s = s.replace('"Gemma 4 local · ${lastGemmaBackend.ifBlank { "parcial" }}",', '"Gemma 4 local · ${lastGemmaBackend.ifBlank { "parcial" }} · $connectivityLabel$webUsageSuffix",')
s = s.replace('return markEngine("Local básico", basic)', 'return markEngine("Local básico · $connectivityLabel", basic)')

old = '''        if (!isConfigured()) return localFallback()

        val sourceText = buildString {'''
new = '''        // En Automático, no intentes una llamada Mistral cuando Android no tiene Internet validado.
        // Si hay Internet y Mistral está configurado, Mistral sigue siendo el motor online preferido.
        if (!isConfigured() || !internetAvailable) return localFallback()

        val sourceText = buildString {'''
assert old in s, "configured routing anchor not found"
s = s.replace(old, new, 1)

old = '''        return try {
            markEngine("Mistral", sendToMistral(prompt))
        } catch (_: Throwable) {
            localFallback()
        }
    }

    private fun markEngine'''
new = '''        return try {
            markEngine("Mistral · online", sendToMistral(prompt))
        } catch (_: Throwable) {
            localFallback()
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun markEngine'''
assert old in s, "markEngine anchor not found"
s = s.replace(old, new, 1)
service.write_text(s)

# 2) Avoid unnecessary auto-web context for stable one-line definitions.
web = root / "app/src/main/java/com/notcan/app/ai/WebResearchService.kt"
w = web.read_text()
old = '''            if (localOnlyHints.any { it in q }) return false

            return true'''
new = '''            if (localOnlyHints.any { it in q }) return false

            // Una definición estable y breve no necesita pagar latencia ni miles de caracteres
            // de contexto web. La búsqueda forzada sigue funcionando mediante BUSCAR_WEB_NOTCAN.
            val freshnessOrVerificationHints = listOf(
                "hoy", "actual", "actualmente", "ultimo", "último", "reciente", "2026",
                "noticia", "noticias", "fuente oficial", "fuentes oficiales", "cita textual",
                "texto oficial", "documento oficial", "vatican", "vaticano", "web", "internet",
                "busca", "buscar", "verifica", "verificar", "comprueba", "confirma"
            )
            val needsFreshOrVerifiedWeb = freshnessOrVerificationHints.any { it in q }
            val stableDefinition = q.length <= 140 && listOf(
                "que es ", "qué es ", "que significa ", "qué significa ", "define ",
                "definicion de ", "definición de "
            ).any(q::startsWith)
            if (stableDefinition && !needsFreshOrVerifiedWeb) return false

            return true'''
assert old in w, "shouldAutoSearch anchor not found"
w = w.replace(old, new, 1)
web.write_text(w)

# 3) Adaptive local system prompt for simple definitions + stricter theological terminology.
engine = root / "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt"
e = engine.read_text()
old = 'systemInstruction = Contents.of(buildSystemInstruction(strictSources, pedagogicalMode)),'
new = 'systemInstruction = Contents.of(buildAdaptiveSystemInstruction(strictSources, pedagogicalMode, intentQuestion)),'
assert old in e, "conversation system instruction anchor not found"
e = e.replace(old, new, 1)

anchor = '''    private fun buildSystemInstruction(strictSources: Boolean, pedagogicalMode: Boolean): String = buildString {
'''
helper = '''    private fun isTheologicalPrecisionQuery(question: String): Boolean {
        val n = normalize(question)
        return listOf(
            "hipostasis", "hypostasis", "ousia", "trinidad", "trinitario", "trinitaria",
            "persona divina", "naturaleza divina", "cristologia", "cristologico", "cristologica",
            "encarnacion", "verbo", "consubstancial", "consustancial"
        ).any(n::contains)
    }

    private fun buildAdaptiveSystemInstruction(
        strictSources: Boolean,
        pedagogicalMode: Boolean,
        question: String
    ): String {
        if (!isSimpleDefinition(question)) return buildSystemInstruction(strictSources, pedagogicalMode)

        return buildString {
            appendLine("Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.")
            appendLine("Responde en español claro, natural y preciso. Para una definición puntual responde en 1–2 párrafos breves y detente.")
            appendLine("No inventes citas, páginas, autores, fechas ni referencias. No muestres razonamiento interno.")
            appendLine("Usa Markdown simple y no uses LaTeX salvo que el estudiante lo pida.")
            if (preferences.aiInstructions.isNotBlank()) {
                appendLine("Preferencias del estudiante: ${preferences.aiInstructions}")
            }
            if (isTheologicalPrecisionQuery(question)) {
                appendLine("En teología católica usa terminología patrística, trinitaria y cristológica con precisión.")
                appendLine("En la formulación trinitaria madura no presentes hipóstasis como sinónimo de ousia: una única ousia o naturaleza divina y tres hipóstasis o Personas realmente distintas y consustanciales.")
                appendLine("Si mencionas que hypostasis y ousia tuvieron usos históricos solapados, indícalo explícitamente como una cuestión histórica de terminología y no como equivalencia doctrinal trinitaria.")
                appendLine("En cristología: Jesucristo es una sola Persona o hipóstasis, la del Verbo, en dos naturalezas, divina y humana, sin confusión ni división.")
            }
            if (pedagogicalMode) {
                appendLine("Explica de manera pedagógica sin convertir una definición breve en un ensayo.")
            }
            if (strictSources) {
                appendLine("Está activado Solo mis fuentes: no añadas conocimiento externo al material suministrado.")
            }
        }.trim()
    }

'''
assert anchor in e, "buildSystemInstruction anchor not found"
e = e.replace(anchor, helper + anchor, 1)
engine.write_text(e)

# 4) Version bump.
gradle = root / "app/build.gradle.kts"
g = gradle.read_text()
assert 'versionCode = 58' in g and 'versionName = "0.8.34"' in g, "version anchor not found"
g = g.replace('versionCode = 58', 'versionCode = 59', 1)
g = g.replace('versionName = "0.8.34"', 'versionName = "0.8.35"', 1)
gradle.write_text(g)

print("Applied NotCan v0.8.35 TuNot connectivity + adaptive prompt tuning")
