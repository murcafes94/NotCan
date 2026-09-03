from pathlib import Path

root = Path(__file__).resolve().parents[1]

# 1) Move to the first LiteRT-LM release that exposes ConversationConfig.maxOutputToken.
gradle = root / "app/build.gradle.kts"
gradle_text = gradle.read_text()
old_dep = 'implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")'
new_dep = 'implementation("com.google.ai.edge.litertlm:litertlm-android:0.15.0")'
if old_dep not in gradle_text and new_dep not in gradle_text:
    raise SystemExit("LiteRT-LM dependency line not found")
if old_dep in gradle_text:
    gradle.write_text(gradle_text.replace(old_dep, new_dep, 1))

# 2) Give Gemma a native output-token budget based on the user's request.
engine = root / "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt"
s = engine.read_text()
old_config = '''        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(buildSystemInstruction(strictSources)),
            samplerConfig = SamplerConfig(
                topK = TOP_K,
                topP = TOP_P,
                temperature = TEMPERATURE
            )
        )'''
new_config = '''        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(buildSystemInstruction(strictSources)),
            samplerConfig = SamplerConfig(
                topK = TOP_K,
                topP = TOP_P,
                temperature = TEMPERATURE
            ),
            maxOutputToken = outputTokenBudget(question)
        )'''
if old_config in s:
    s = s.replace(old_config, new_config, 1)
elif 'maxOutputToken = outputTokenBudget(question)' not in s:
    raise SystemExit("ConversationConfig block not found")

anchor = '''    private fun isResponseTransformRequest(question: String): Boolean {
'''
helper = '''    private fun outputTokenBudget(question: String): Int {
        val n = normalize(question)
        val explicitlyBrief = isResponseTransformRequest(question) || listOf(
            "brevemente", "respuesta breve", "responde breve", "una frase", "en una frase",
            "solo una frase", "muy corto", "muy breve"
        ).any(n::contains)
        if (explicitlyBrief) return 96

        val explicitlyDetailed = listOf(
            "profundiza", "profundizar", "detalladamente", "con detalle", "desarrolla",
            "desarrollalo", "explicacion completa", "explicacion profunda", "amplia"
        ).any(n::contains)
        if (explicitlyDetailed) return 512

        if (isBroadSourceRequest(question)) return 448
        if (isSourceOverviewRequest(question)) return 320
        return 192
    }

'''
if helper not in s:
    if anchor not in s:
        raise SystemExit("outputTokenBudget insertion anchor not found")
    s = s.replace(anchor, helper + anchor, 1)

old_instruction = '''        appendLine("Responde siempre a la pregunta actual; no repitas una respuesta anterior si ya no corresponde al tema preguntado.")
        appendLine("Nivel de detalle preferido: ${preferences.aiDetail}.")'''
new_instruction = '''        appendLine("Responde siempre a la pregunta actual; no repitas una respuesta anterior si ya no corresponde al tema preguntado.")
        appendLine("Sé conciso por defecto: responde lo necesario para resolver la pregunta y detente. Amplía solo si el estudiante lo pide o si la tarea exige un resumen/desarrollo amplio.")
        appendLine("Nivel de detalle preferido: ${preferences.aiDetail}.")'''
if old_instruction in s:
    s = s.replace(old_instruction, new_instruction, 1)
elif 'Sé conciso por defecto' not in s:
    raise SystemExit("system instruction anchor not found")
engine.write_text(s)

# 3) If Gemma times out after producing useful streamed text, keep that text instead of
# replacing it with an unrelated Local básico answer.
service = root / "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
t = service.read_text()
old_try = '''            if (allowGemma && llmEligible && localGemma.isAvailable()) {
                try {
                    val answer = localGemma.answer(
                        subjectName = subjectName,
                        notes = plainNotes,
                        transcript = plainTranscript,
                        question = localQuestion,
                        strictSources = strictSources,
                        onPartial = { partialText, backendLabel ->
                            onPartial?.invoke(markEngine("Gemma 4 local · $backendLabel", partialText))
                        }
                    )
                    preferences.lastLocalAiError = ""
                    return markEngine("Gemma 4 local · ${answer.backendLabel}", answer.text)
                } catch (t: Throwable) {
                    onPartial?.invoke("")
                    preferences.lastLocalAiError = "Gemma 4: ${t.message ?: t.javaClass.simpleName}"
                }
            }'''
new_try = '''            if (allowGemma && llmEligible && localGemma.isAvailable()) {
                var lastGemmaPartial = ""
                var lastGemmaBackend = ""
                try {
                    val answer = localGemma.answer(
                        subjectName = subjectName,
                        notes = plainNotes,
                        transcript = plainTranscript,
                        question = localQuestion,
                        strictSources = strictSources,
                        onPartial = { partialText, backendLabel ->
                            lastGemmaPartial = partialText
                            lastGemmaBackend = backendLabel
                            onPartial?.invoke(markEngine("Gemma 4 local · $backendLabel", partialText))
                        }
                    )
                    preferences.lastLocalAiError = ""
                    return markEngine("Gemma 4 local · ${answer.backendLabel}", answer.text)
                } catch (t: Throwable) {
                    val errorText = t.message ?: t.javaClass.simpleName
                    if (lastGemmaPartial.trim().length >= MIN_USABLE_GEMMA_PARTIAL_CHARS) {
                        preferences.lastLocalAiError = "Gemma 4 (respuesta parcial conservada): $errorText"
                        return markEngine(
                            "Gemma 4 local · ${lastGemmaBackend.ifBlank { "parcial" }}",
                            lastGemmaPartial.trim()
                        )
                    }
                    onPartial?.invoke("")
                    preferences.lastLocalAiError = "Gemma 4: $errorText"
                }
            }'''
if old_try in t:
    t = t.replace(old_try, new_try, 1)
elif 'MIN_USABLE_GEMMA_PARTIAL_CHARS' not in t:
    raise SystemExit("Gemma localFallback block not found")

companion_anchor = '''        private const val READ_TIMEOUT_MS = 90_000
'''
companion_replacement = '''        private const val READ_TIMEOUT_MS = 90_000
        private const val MIN_USABLE_GEMMA_PARTIAL_CHARS = 120
'''
if companion_anchor in t:
    t = t.replace(companion_anchor, companion_replacement, 1)
elif 'MIN_USABLE_GEMMA_PARTIAL_CHARS = 120' not in t:
    raise SystemExit("NotCanAiService companion anchor not found")
service.write_text(t)

print("Gemma output-budget migration applied")
