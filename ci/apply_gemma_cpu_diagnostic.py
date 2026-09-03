from pathlib import Path

p = Path('app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt')
s = p.read_text()

s = s.replace('import android.content.Context\n', 'import android.content.Context\nimport android.os.SystemClock\n', 1)

old_catch = '''        val output = StringBuilder()
        try {
            withTimeout(GENERATION_TIMEOUT_MS) {
                engineHolder.engine.createConversation(conversationConfig).use { conversation ->
                    conversation.sendMessageAsync(prompt).collect { message ->
                        val delta = message.toString()
                        if (delta.isNotEmpty()) {
                            output.append(delta)
                            onPartial?.invoke(output.toString(), engineHolder.backendLabel)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            resetEngine()
            throw t
        }
'''
new_catch = '''        val output = StringBuilder()
        val generationStartedAt = SystemClock.elapsedRealtime()
        try {
            withTimeout(GENERATION_TIMEOUT_MS) {
                engineHolder.engine.createConversation(conversationConfig).use { conversation ->
                    conversation.sendMessageAsync(prompt).collect { message ->
                        val delta = message.toString()
                        if (delta.isNotEmpty()) {
                            output.append(delta)
                            onPartial?.invoke(output.toString(), engineHolder.backendLabel)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            val elapsedMs = SystemClock.elapsedRealtime() - generationStartedAt
            val generatedChars = output.length
            resetEngine()
            throw IllegalStateException(
                "${engineHolder.backendLabel}: ${t.message ?: t.javaClass.simpleName}; ${elapsedMs} ms; ${generatedChars} caracteres generados",
                t
            )
        }
'''
if old_catch not in s:
    raise SystemExit('generation block not found')
s = s.replace(old_catch, new_catch, 1)

start = s.index('    @OptIn(ExperimentalApi::class)\n    private suspend fun ensureEngineReady(): EngineHolder {')
end = s.index('\n    private fun createEngine(modelPath: String, backend: Backend): Engine = Engine(', start)
new_engine = '''    @OptIn(ExperimentalApi::class)
    private suspend fun ensureEngineReady(): EngineHolder {
        holder?.let { return it }
        return withContext(Dispatchers.IO) {
            holder?.let { return@withContext it }

            // Diagnostic build: force CPU so we can isolate a GPU runtime stall from
            // a general Gemma/LiteRT problem on this device.
            ExperimentalFlags.enableSpeculativeDecoding = false
            val modelPath = modelManager.modelFile().absolutePath
            val cpuEngine = runCatching {
                createEngine(modelPath, Backend.CPU()).also { it.initialize() }
            }.getOrElse { cpuError ->
                throw IllegalStateException(
                    "LiteRT-LM no pudo iniciar Gemma 4 en CPU: ${cpuError.message ?: cpuError.javaClass.simpleName}",
                    cpuError
                )
            }
            EngineHolder(cpuEngine, "CPU diagnóstico").also { holder = it }
        }
    }
'''
s = s[:start] + new_engine + s[end:]

p.write_text(s)
print('Gemma CPU diagnostic patch applied')
