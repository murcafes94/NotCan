from pathlib import Path

build = Path('app/build.gradle.kts')
text = build.read_text()
old = 'implementation("com.google.ai.edge.litertlm:litertlm-android:0.15.0")'
new = 'implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")'
assert old in text, 'LiteRT 0.15 dependency not found'
build.write_text(text.replace(old, new, 1))

p = Path('app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt')
s = p.read_text()

old = '''    private class GenerationException(\n        val backendLabel: String,\n        val elapsedMs: Long,\n        val generatedChars: Int,\n        cause: Throwable\n    ) : IllegalStateException(\n        "$backendLabel: ${cause.message ?: cause.javaClass.simpleName}; $elapsedMs ms; $generatedChars caracteres generados",\n        cause\n    )'''
new = '''    private class GenerationException(\n        val backendLabel: String,\n        val elapsedMs: Long,\n        val generatedChars: Int,\n        val partialText: String,\n        cause: Throwable\n    ) : IllegalStateException(\n        "$backendLabel: ${cause.message ?: cause.javaClass.simpleName}; $elapsedMs ms; $generatedChars caracteres generados",\n        cause\n    )'''
assert old in s, 'GenerationException block not found'
s = s.replace(old, new, 1)

old = '''            appendLine()\n            appendLine("Pregunta actual del estudiante:")\n            append(question.trim())'''
new = '''            appendLine()\n            appendLine(responseLengthInstruction(question))\n            appendLine("Pregunta actual del estudiante:")\n            append(question.trim())'''
assert old in s, 'Prompt tail not found'
s = s.replace(old, new, 1)

old = '''        val conversationConfig = ConversationConfig(\n            systemInstruction = Contents.of(buildSystemInstruction(strictSources)),\n            samplerConfig = SamplerConfig(\n                topK = TOP_K,\n                topP = TOP_P,\n                temperature = TEMPERATURE\n            ),\n            maxOutputToken = outputTokenBudget(question)\n        )'''
new = '''        val conversationConfig = ConversationConfig(\n            systemInstruction = Contents.of(buildSystemInstruction(strictSources)),\n            samplerConfig = SamplerConfig(\n                topK = TOP_K,\n                topP = TOP_P,\n                temperature = TEMPERATURE\n            )\n        )'''
assert old in s, '0.15 ConversationConfig block not found'
s = s.replace(old, new, 1)

old = '''        } catch (t: GenerationException) {\n            if (primaryHolder.backendLabel == "GPU" && t.generatedChars == 0) {\n                resetEngine()\n                val cpuHolder = ensureCpuEngineReady("CPU respaldo")\n                generate(cpuHolder, prompt, conversationConfig, onPartial)\n            } else {\n                resetEngine()\n                throw t\n            }'''
new = '''        } catch (t: GenerationException) {\n            val recovered = recoverUsefulPartial(t.partialText)\n            if (recovered != null) {\n                resetEngine()\n                Answer(recovered, "${t.backendLabel} · respuesta recuperada")\n            } else if (primaryHolder.backendLabel == "GPU" && t.generatedChars == 0) {\n                resetEngine()\n                val cpuHolder = ensureCpuEngineReady("CPU respaldo")\n                generate(cpuHolder, prompt, conversationConfig, onPartial)\n            } else {\n                resetEngine()\n                throw t\n            }'''
assert old in s, 'GenerationException catch block not found'
s = s.replace(old, new, 1)

old = '''            throw GenerationException(\n                backendLabel = engineHolder.backendLabel,\n                elapsedMs = SystemClock.elapsedRealtime() - generationStartedAt,\n                generatedChars = output.length,\n                cause = t\n            )'''
new = '''            throw GenerationException(\n                backendLabel = engineHolder.backendLabel,\n                elapsedMs = SystemClock.elapsedRealtime() - generationStartedAt,\n                generatedChars = output.length,\n                partialText = output.toString(),\n                cause = t\n            )'''
assert old in s, 'Generation catch exception block not found'
s = s.replace(old, new, 1)

old = '''            throw GenerationException(\n                backendLabel = engineHolder.backendLabel,\n                elapsedMs = SystemClock.elapsedRealtime() - generationStartedAt,\n                generatedChars = 0,\n                cause = IllegalStateException("Gemma 4 no produjo texto utilizable")\n            )'''
new = '''            throw GenerationException(\n                backendLabel = engineHolder.backendLabel,\n                elapsedMs = SystemClock.elapsedRealtime() - generationStartedAt,\n                generatedChars = 0,\n                partialText = "",\n                cause = IllegalStateException("Gemma 4 no produjo texto utilizable")\n            )'''
assert old in s, 'Blank output exception block not found'
s = s.replace(old, new, 1)

start = s.index('    private fun outputTokenBudget(question: String): Int {')
end = s.index('    private fun isResponseTransformRequest(question: String): Boolean {', start)
replacement = '''    private fun responseLengthInstruction(question: String): String {\n        val n = normalize(question)\n        val explicitlyBrief = isResponseTransformRequest(question) || listOf(\n            "brevemente", "respuesta breve", "responde breve", "una frase", "en una frase",\n            "solo una frase", "muy corto", "muy breve"\n        ).any(n::contains)\n        if (explicitlyBrief) return "Extensión: responde en 1–3 frases, sin introducción ni repetición."\n\n        val explicitlyDetailed = listOf(\n            "profundiza", "profundizar", "detalladamente", "con detalle", "desarrolla",\n            "desarrollalo", "explicacion completa", "explicacion profunda", "amplia"\n        ).any(n::contains)\n        if (explicitlyDetailed) return "Extensión: desarrolla con detalle, pero evita repeticiones y termina en cuanto la explicación quede completa."\n\n        if (isBroadSourceRequest(question)) return "Extensión: ofrece un resumen estructurado y suficiente, sin repetir ideas."\n        if (isSourceOverviewRequest(question)) return "Extensión: responde en 2–4 párrafos breves y centrados en la fuente."\n        return "Extensión: responde de forma concisa; normalmente 2–5 párrafos breves son suficientes."\n    }\n\n    private fun recoverUsefulPartial(raw: String): String? {\n        val text = raw.trim()\n        if (text.length < MIN_USEFUL_PARTIAL_CHARS) return null\n        val lastSentence = maxOf(text.lastIndexOf('.'), text.lastIndexOf('!'), text.lastIndexOf('?'))\n        return if (lastSentence >= MIN_USEFUL_PARTIAL_CHARS - 1) {\n            text.substring(0, lastSentence + 1).trim()\n        } else {\n            text\n        }\n    }\n\n'''
s = s[:start] + replacement + s[end:]

old = '''        appendLine("Responde en español claro, natural, preciso y útil para estudiar.")'''
new = '''        appendLine("Responde en español claro, natural, preciso y útil para estudiar.")\n        appendLine("Sé conciso por defecto: responde solo con la extensión necesaria y evita repetir la misma idea.")'''
assert old in s, 'System instruction line not found'
s = s.replace(old, new, 1)

old = '''        private const val GENERATION_TIMEOUT_MS = 75_000L'''
new = '''        private const val GENERATION_TIMEOUT_MS = 75_000L\n        private const val MIN_USEFUL_PARTIAL_CHARS = 180'''
assert old in s, 'Timeout constant not found'
s = s.replace(old, new, 1)

p.write_text(s)
print('Applied LiteRT 0.11 rollback + safe partial recovery')
