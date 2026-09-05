from pathlib import Path

root = Path(__file__).resolve().parents[1]
service = root / "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
engine = root / "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt"
gradle = root / "app/build.gradle.kts"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    if old not in text:
        raise SystemExit(f"No se encontró el ancla: {label}")
    return text.replace(old, new, 1)


# 1) Motor routing: Local básico is extractive; without sources, use installed Gemma.
s = service.read_text()
s = replace_once(
    s,
    '''        val vocabularyContext = runCatching { loadVocabularyContext(subjectName, vocabularyRequested) }.getOrDefault("")

        if (strictSources && plainNotes.isBlank() && plainTranscript.isBlank() && vocabularyContext.isBlank()) {''',
    '''        val vocabularyContext = runCatching { loadVocabularyContext(subjectName, vocabularyRequested) }.getOrDefault("")
        val hasLocalStudyMaterial = plainNotes.isNotBlank() || plainTranscript.isNotBlank() || vocabularyContext.isNotBlank()

        if (strictSources && plainNotes.isBlank() && plainTranscript.isBlank() && vocabularyContext.isBlank()) {''',
    "hasLocalStudyMaterial",
)
s = replace_once(
    s,
    '''        when (preferences.aiEnginePreference) {
            "Gemma 4 local" -> return localFallback(allowGemma = true)
            "Local básico" -> return localFallback(allowGemma = false)
        }''',
    '''        when (preferences.aiEnginePreference) {
            "Gemma 4 local" -> return localFallback(allowGemma = true)
            "Local básico" -> {
                // El motor extractivo necesita fuentes. Sin fuentes y fuera de Solo mis fuentes,
                // Gemma es el respaldo local útil aunque no haya Internet.
                val shouldEscalateToGemma = !strictSources && !hasLocalStudyMaterial && localGemma.isAvailable()
                return localFallback(allowGemma = shouldEscalateToGemma)
            }
        }''',
    "Local básico -> Gemma",
)
service.write_text(s)

# 2) Gemma: smaller context for simple definitions and a real streaming output budget.
e = engine.read_text()
e = replace_once(
    e,
    '''            if (vocabularyContext.isNotBlank()) {
                appendLine()
                appendLine("--- VOCABULARIO ACADÉMICO DE NOTCAN ---")''',
    '''            if (vocabularyContext.isNotBlank() && (!isSimpleDefinition(intentQuestion) || strictSources)) {
                appendLine()
                appendLine("--- VOCABULARIO ACADÉMICO DE NOTCAN ---")''',
    "vocabulary context",
)
e = replace_once(
    e,
    '''        val generationTimeoutMs = generationTimeoutMs(intentQuestion)
        val engineWasWarm = holder != null''',
    '''        val generationTimeoutMs = generationTimeoutMs(intentQuestion)
        val maxOutputChars = outputCharBudget(intentQuestion)
        val engineWasWarm = holder != null''',
    "output budget setup",
)
e = replace_once(
    e,
    'generate(primaryHolder, prompt, conversationConfig, generationTimeoutMs, onPartial)',
    'generate(primaryHolder, prompt, conversationConfig, generationTimeoutMs, maxOutputChars, onPartial)',
    "GPU generate call",
)
e = replace_once(
    e,
    'generate(cpuHolder, prompt, conversationConfig, generationTimeoutMs, onPartial)',
    'generate(cpuHolder, prompt, conversationConfig, generationTimeoutMs, maxOutputChars, onPartial)',
    "CPU generate call",
)
e = replace_once(
    e,
    '''        conversationConfig: ConversationConfig,
        timeoutMs: Long,
        onPartial: ((text: String, backendLabel: String) -> Unit)?''',
    '''        conversationConfig: ConversationConfig,
        timeoutMs: Long,
        maxOutputChars: Int,
        onPartial: ((text: String, backendLabel: String) -> Unit)?''',
    "generate signature",
)
e = replace_once(
    e,
    '''                    firstMessage?.toString()?.takeIf { it.isNotEmpty() }?.let { delta ->
                        if (firstTokenMs == 0L) firstTokenMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)
                        output.append(delta)
                        onPartial?.invoke(cleanModelText(output.toString()), engineHolder.backendLabel)
                    }

                    for (message in messages) {
                        val delta = message.toString()
                        if (delta.isNotEmpty()) {
                            if (firstTokenMs == 0L) firstTokenMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)
                            output.append(delta)
                            onPartial?.invoke(cleanModelText(output.toString()), engineHolder.backendLabel)
                        }
                    }''',
    '''                    var stopRequested = false
                    fun appendDelta(delta: String) {
                        if (delta.isEmpty() || stopRequested) return
                        if (firstTokenMs == 0L) firstTokenMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)
                        output.append(delta)
                        onPartial?.invoke(cleanModelText(output.toString()), engineHolder.backendLabel)
                        if (shouldStopGeneration(output, maxOutputChars)) stopRequested = true
                    }

                    firstMessage?.toString()?.let(::appendDelta)
                    if (stopRequested) {
                        messages.cancel()
                    } else {
                        for (message in messages) {
                            appendDelta(message.toString())
                            if (stopRequested) {
                                messages.cancel()
                                break
                            }
                        }
                    }''',
    "stream loop",
)
e = replace_once(
    e,
    '        val text = cleanModelText(output.toString()).trim()',
    '        val text = finalizeBoundedOutput(output.toString(), maxOutputChars)',
    "final bounded output",
)
e = replace_once(
    e,
    '''        val tokens = queryTokens(question)
        val artifactRequest = isStudyArtifactRequest(question)
        val broadRequest = isBroadSourceRequest(question)''',
    '''        val tokens = queryTokens(question)
        val artifactRequest = isStudyArtifactRequest(question)
        val simpleDefinition = isSimpleDefinition(question)
        val broadRequest = isBroadSourceRequest(question)''',
    "simple definition RAG flag",
)
e = replace_once(
    e,
    '''            tokens.isNotEmpty() -> scored
                .filter { it.score > 0 }
                .sortedByDescending { it.score }
                .take(FOCUSED_SELECTED_CHUNKS)''',
    '''            tokens.isNotEmpty() -> scored
                .filter { it.score > 0 }
                .sortedByDescending { it.score }
                .take(if (simpleDefinition && !strictSources) SIMPLE_DEFINITION_SELECTED_CHUNKS else FOCUSED_SELECTED_CHUNKS)''',
    "simple definition RAG chunks",
)
e = replace_once(
    e,
    '''        val sourceCharLimit = when {
            artifactRequest -> MAX_ARTIFACT_SOURCE_CHARS
            broadRequest -> MAX_BROAD_SOURCE_CHARS''',
    '''        val sourceCharLimit = when {
            artifactRequest -> MAX_ARTIFACT_SOURCE_CHARS
            simpleDefinition && !strictSources -> MAX_SIMPLE_DEFINITION_SOURCE_CHARS
            broadRequest -> MAX_BROAD_SOURCE_CHARS''',
    "simple definition RAG chars",
)

helper_anchor = '''    private fun responseLengthInstruction(question: String): String {
'''
helpers = '''    private fun isSimpleDefinition(question: String): Boolean {
        val n = normalize(question)
        return n.length <= 110 && listOf(
            "que es ", "que significa ", "define ", "definicion de ", "explica el ", "explica la "
        ).any(n::startsWith)
    }

    private fun outputCharBudget(question: String): Int {
        if (isStudyArtifactRequest(question)) return Int.MAX_VALUE
        val n = normalize(question)
        val explicitlyBrief = isResponseTransformRequest(question) || listOf(
            "brevemente", "respuesta breve", "responde breve", "una frase", "en una frase",
            "solo una frase", "muy corto", "muy breve"
        ).any(n::contains)
        if (explicitlyBrief) return 420

        val explicitlyDetailed = listOf(
            "profundiza", "profundizar", "detalladamente", "con detalle", "desarrolla",
            "desarrollalo", "explicacion completa", "explicacion profunda", "amplia", "a fondo"
        ).any(n::contains)
        if (explicitlyDetailed) return 5_200
        if (isBroadSourceRequest(question)) return 6_500
        if (isSourceOverviewRequest(question)) return 4_200
        if (isSimpleDefinition(question)) return 760

        return when (preferences.aiDetail.lowercase()) {
            "breve" -> 900
            "profundo" -> 4_200
            else -> 1_800
        }
    }

    private fun shouldStopGeneration(output: StringBuilder, softLimit: Int): Boolean {
        if (softLimit == Int.MAX_VALUE || output.length < softLimit) return false
        val tail = output.takeLast(140).trimEnd()
        val last = tail.lastOrNull()
        val sentenceBoundary = last == '.' || last == '!' || last == '?'
        return sentenceBoundary || output.length >= softLimit + OUTPUT_HARD_MARGIN_CHARS
    }

    private fun finalizeBoundedOutput(raw: String, softLimit: Int): String {
        val cleaned = cleanModelText(raw).trim()
        if (softLimit == Int.MAX_VALUE || cleaned.length <= softLimit) return cleaned
        val last = cleaned.lastOrNull()
        if (last == '.' || last == '!' || last == '?') return cleaned

        val candidate = cleaned.take((softLimit + OUTPUT_HARD_MARGIN_CHARS).coerceAtMost(cleaned.length))
        val sentenceEnd = maxOf(candidate.lastIndexOf('.'), candidate.lastIndexOf('!'), candidate.lastIndexOf('?'))
        return if (sentenceEnd >= softLimit / 2) candidate.substring(0, sentenceEnd + 1).trim() else candidate.trim()
    }

'''
if helpers not in e:
    if helper_anchor not in e:
        raise SystemExit("No se encontró el ancla de helpers")
    e = e.replace(helper_anchor, helpers + helper_anchor, 1)

e = replace_once(
    e,
    '''        val simpleDefinition = n.length <= 100 && listOf("que es ", "define ", "explica el ", "explica la ").any(n::startsWith)
        if (simpleDefinition && !preferences.aiDetail.equals("Profundo", ignoreCase = true)) return "Extensión: responde en 1–2 párrafos breves (aprox. 60–140 palabras), sin apartados numerados salvo que se pidan. Define primero el término y añade solo la distinción o contexto esencial; evita convertir una pregunta puntual en un ensayo."''',
    '''        if (isSimpleDefinition(question)) return "Extensión: responde en 1–2 párrafos breves (aprox. 50–110 palabras), sin apartados numerados salvo que se pidan. Define primero el término y añade solo la distinción o contexto esencial. Aunque el nivel general sea Profundo, no conviertas una definición puntual en un ensayo si el estudiante no pidió profundizar."''',
    "definition length instruction",
)
e = replace_once(
    e,
    '        appendLine("Adapta la extensión a la dificultad de la pregunta y al nivel de detalle elegido por el estudiante; no recortes una explicación académica útil solo por ser larga.")',
    '        appendLine("Adapta la extensión a lo que se pregunta. Las definiciones y preguntas puntuales deben ser breves por defecto, incluso si el nivel general es Profundo; amplía solo cuando el estudiante lo pida. Las tareas de desarrollo, síntesis o estudio amplio sí pueden ser extensas.")',
    "system length policy",
)
e = replace_once(
    e,
    '        appendLine("En terminología patrística, trinitaria y cristológica conserva con rigor las distinciones entre naturaleza/esencia (ousia, physis), hipóstasis/persona y prosopon; no identifiques sin más hipóstasis o persona con esencia o naturaleza. Si una equivalencia es discutida o depende del autor/época, indícalo con prudencia.")',
    '''        appendLine("En terminología patrística, trinitaria y cristológica conserva con rigor las distinciones entre naturaleza/esencia (ousia, physis), hipóstasis/persona y prosopon; no identifiques sin más hipóstasis o persona con esencia o naturaleza. Si una equivalencia es discutida o depende del autor/época, indícalo con prudencia.")
        appendLine("En teología trinitaria católica no describas al Padre, al Hijo y al Espíritu Santo como tres modos, manifestaciones o formas en que se presenta una sola persona. Formula con precisión: una única esencia o naturaleza divina (ousia) y tres Personas o hipóstasis realmente distintas y consustanciales; la distinción personal no divide la esencia divina.")
        appendLine("Cuando expliques hipóstasis, distingue sus usos filosófico/patrístico, trinitario y cristológico. En cristología, Jesucristo es una sola Persona o hipóstasis, la del Verbo, en dos naturalezas, divina y humana, sin confusión ni división.")''',
    "Trinitarian terminology policy",
)
e = replace_once(
    e,
    '''        private const val MAX_OVERVIEW_SOURCE_CHARS = 4_200
        private const val MAX_FOCUSED_SOURCE_CHARS = 1_600''',
    '''        private const val MAX_OVERVIEW_SOURCE_CHARS = 4_200
        private const val MAX_SIMPLE_DEFINITION_SOURCE_CHARS = 700
        private const val MAX_FOCUSED_SOURCE_CHARS = 1_600''',
    "simple source char constant",
)
e = replace_once(
    e,
    '''        private const val OVERVIEW_SELECTED_CHUNKS = 4
        private const val FOCUSED_SELECTED_CHUNKS = 2''',
    '''        private const val OVERVIEW_SELECTED_CHUNKS = 4
        private const val SIMPLE_DEFINITION_SELECTED_CHUNKS = 1
        private const val FOCUSED_SELECTED_CHUNKS = 2''',
    "simple source chunk constant",
)
e = replace_once(
    e,
    '''        private const val MIN_USEFUL_PARTIAL_CHARS = 180
        private const val KEY_CAPABILITIES_FINGERPRINT''',
    '''        private const val MIN_USEFUL_PARTIAL_CHARS = 180
        private const val OUTPUT_HARD_MARGIN_CHARS = 240
        private const val KEY_CAPABILITIES_FINGERPRINT''',
    "hard output margin constant",
)
engine.write_text(e)

# 3) Version bump. Keep LiteRT-LM 0.11.0 because it is the GPU-validated runtime.
g = gradle.read_text()
if 'versionName = "0.8.34"' not in g:
    if 'versionCode = 57' not in g or 'versionName = "0.8.33.1"' not in g:
        raise SystemExit("Versión base inesperada")
    g = g.replace('versionCode = 57', 'versionCode = 58', 1)
    g = g.replace('versionName = "0.8.33.1"', 'versionName = "0.8.34"', 1)
    gradle.write_text(g)

print("NotCan v0.8.34 patch ready")
