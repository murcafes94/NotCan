from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"pattern not found in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))


engine = "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt"
replace(engine,
        "import com.notcan.app.localai.GemmaLiteRtModelState\n",
        "import com.notcan.app.localai.GemmaLiteRtModelState\nimport com.notcan.app.localai.GemmaRuntimeCache\n")
replace(engine,
        "import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.NonCancellable\nimport kotlinx.coroutines.coroutineScope\n",
        "import kotlinx.coroutines.CoroutineScope\nimport kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.Job\nimport kotlinx.coroutines.NonCancellable\nimport kotlinx.coroutines.SupervisorJob\nimport kotlinx.coroutines.coroutineScope\nimport kotlinx.coroutines.delay\nimport kotlinx.coroutines.launch\n")
replace(engine,
        "    private val performanceMetrics = com.notcan.app.performance.PerformanceMetricsStore(appContext)\n",
        "    private val performanceMetrics = com.notcan.app.performance.PerformanceMetricsStore(appContext)\n    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)\n    private var idleReleaseJob: Job? = null\n")
replace(engine,
        "    fun isAvailable(): Boolean = runCatching {\n        modelManager.state() == GemmaLiteRtModelState.INSTALLED\n    }.getOrDefault(false)\n\n    suspend fun answer(\n",
        "    fun isAvailable(): Boolean = runCatching {\n        modelManager.state() == GemmaLiteRtModelState.INSTALLED\n    }.getOrDefault(false)\n\n    suspend fun warmUp(): String? = mutex.withLock {\n        if (!isAvailable()) return@withLock null\n        idleReleaseJob?.cancel()\n        val engineWasWarm = holder != null\n        val startedAt = SystemClock.elapsedRealtime()\n        val ready = ensureEngineReady()\n        if (!engineWasWarm) {\n            performanceMetrics.recordGemmaLoad(\n                (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L),\n                ready.backendLabel\n            )\n        }\n        scheduleIdleRelease()\n        ready.backendLabel\n    }\n\n    suspend fun answer(\n")
replace(engine,
        "        check(isAvailable()) { \"Gemma 4 LiteRT-LM no está instalado\" }\n\n        val subjectKey",
        "        check(isAvailable()) { \"Gemma 4 LiteRT-LM no está instalado\" }\n        idleReleaseJob?.cancel()\n\n        val subjectKey")
replace(engine,
        "            } else if (primaryHolder.backendLabel == \"GPU\" && t.generatedChars == 0) {\n                resetEngine()\n",
        "            } else if (primaryHolder.backendLabel == \"GPU\" && t.generatedChars == 0) {\n                performanceMetrics.recordGemmaFallback(\"GPU sin primer token en ${GPU_FIRST_TOKEN_TIMEOUT_MS / 1_000L} s\")\n                resetEngine()\n")
replace(engine,
        "        lastAnswerText = answer.text\n        lastAnswerSubject = subjectKey\n        answer\n",
        "        lastAnswerText = answer.text\n        lastAnswerSubject = subjectKey\n        scheduleIdleRelease()\n        answer\n")
replace(engine,
        "        val simpleDefinition = n.length <= 100 && listOf(\"que es \", \"define \", \"explica el \", \"explica la \").any(n::startsWith)\n        if (simpleDefinition) return \"Extensión: responde con una explicación clara y completa en 2–4 párrafos breves; evita convertir una pregunta puntual en un ensayo.\"\n" if False else
        "        if (isBroadSourceRequest(question)) return \"Extensión: desarrolla el recurso o resumen con la amplitud necesaria para cubrir bien el material, evitando solo la repetición.\"\n",
        "        if (isBroadSourceRequest(question)) return \"Extensión: desarrolla el recurso o resumen con la amplitud necesaria para cubrir bien el material, evitando solo la repetición.\"\n        val simpleDefinition = n.length <= 100 && listOf(\"que es \", \"define \", \"explica el \", \"explica la \").any(n::startsWith)\n        if (simpleDefinition && !preferences.aiDetail.equals(\"Profundo\", ignoreCase = true)) return \"Extensión: responde con una explicación clara y completa en 2–4 párrafos breves; evita convertir una pregunta puntual en un ensayo.\"\n")
replace(engine,
        "    private suspend fun resetEngine() {\n",
        "    private fun scheduleIdleRelease() {\n        idleReleaseJob?.cancel()\n        idleReleaseJob = engineScope.launch {\n            delay(ENGINE_IDLE_RELEASE_MS)\n            mutex.withLock { resetEngine() }\n        }\n    }\n\n    private suspend fun resetEngine() {\n")
replace(engine,
        "            gpuAttempt.fold(\n                onSuccess = { EngineHolder(it, \"GPU\").also { ready -> holder = ready } },\n                onFailure = { ensureCpuEngineReady(\"CPU respaldo\") }\n            )\n",
        "            gpuAttempt.fold(\n                onSuccess = { EngineHolder(it, \"GPU\").also { ready -> holder = ready } },\n                onFailure = { error ->\n                    performanceMetrics.recordGemmaFallback(\"GPU no pudo iniciar: ${error.javaClass.simpleName}\")\n                    ensureCpuEngineReady(\"CPU respaldo\")\n                }\n            )\n")
replace(engine,
        "            cacheDir = appContext.cacheDir.absolutePath\n",
        "            cacheDir = GemmaRuntimeCache.directory(appContext).absolutePath\n")
replace(engine,
        "        private const val MAX_VOCAB_CONTEXT_CHARS = 2_200\n",
        "        private const val MAX_VOCAB_CONTEXT_CHARS = 1_000\n")
replace(engine,
        "        private const val MAX_FOCUSED_SOURCE_CHARS = 3_400\n",
        "        private const val MAX_FOCUSED_SOURCE_CHARS = 2_400\n")
replace(engine,
        "        private const val FOCUSED_SELECTED_CHUNKS = 3\n",
        "        private const val FOCUSED_SELECTED_CHUNKS = 2\n")
replace(engine,
        "        private const val GPU_FIRST_TOKEN_TIMEOUT_MS = 30_000L\n",
        "        private const val GPU_FIRST_TOKEN_TIMEOUT_MS = 30_000L\n        private const val ENGINE_IDLE_RELEASE_MS = 10L * 60L * 1_000L\n")

perf = "app/src/main/java/com/notcan/app/performance/PerformanceMetricsStore.kt"
replace(perf,
        "        val gemmaLoadMs: Long,\n        val gemmaBackend: String,\n        val gemmaFirstTokenMs: Long,\n",
        "        val gemmaLoadMs: Long,\n        val gemmaLoadBackend: String,\n        val gemmaBackend: String,\n        val gemmaFallbackReason: String,\n        val gemmaFirstTokenMs: Long,\n")
replace(perf,
        "        gemmaLoadMs = prefs.getLong(KEY_GEMMA_LOAD_MS, 0L),\n        gemmaBackend = prefs.getString(KEY_GEMMA_BACKEND, \"\").orEmpty(),\n        gemmaFirstTokenMs = prefs.getLong(KEY_GEMMA_FIRST_TOKEN_MS, 0L),\n",
        "        gemmaLoadMs = prefs.getLong(KEY_GEMMA_LOAD_MS, 0L),\n        gemmaLoadBackend = prefs.getString(KEY_GEMMA_LOAD_BACKEND, \"\").orEmpty(),\n        gemmaBackend = prefs.getString(KEY_GEMMA_BACKEND, \"\").orEmpty(),\n        gemmaFallbackReason = prefs.getString(KEY_GEMMA_FALLBACK_REASON, \"\").orEmpty(),\n        gemmaFirstTokenMs = prefs.getLong(KEY_GEMMA_FIRST_TOKEN_MS, 0L),\n")
replace(perf,
        "    fun recordGemmaLoad(ms: Long, backend: String) = edit {\n        putLong(KEY_GEMMA_LOAD_MS, ms.coerceAtLeast(0L))\n        putString(KEY_GEMMA_BACKEND, backend)\n    }\n",
        "    fun recordGemmaLoad(ms: Long, backend: String) = edit {\n        putLong(KEY_GEMMA_LOAD_MS, ms.coerceAtLeast(0L))\n        putString(KEY_GEMMA_LOAD_BACKEND, backend)\n    }\n")
replace(perf,
        "    fun recordTranscription(provider: String, processingMs: Long, audioMs: Long) = edit {\n",
        "    fun recordGemmaFallback(reason: String) = edit {\n        putString(KEY_GEMMA_FALLBACK_REASON, reason.take(180))\n    }\n\n    fun recordTranscription(provider: String, processingMs: Long, audioMs: Long) = edit {\n")
replace(perf,
        "        private const val KEY_GEMMA_LOAD_MS = \"gemma_load_ms\"\n        private const val KEY_GEMMA_BACKEND = \"gemma_backend\"\n",
        "        private const val KEY_GEMMA_LOAD_MS = \"gemma_load_ms\"\n        private const val KEY_GEMMA_LOAD_BACKEND = \"gemma_load_backend\"\n        private const val KEY_GEMMA_BACKEND = \"gemma_backend\"\n        private const val KEY_GEMMA_FALLBACK_REASON = \"gemma_fallback_reason\"\n")

settings = "app/src/main/java/com/notcan/app/ui/settings/StoragePerformanceSection.kt"
replace(settings,
        "import com.notcan.app.performance.PerformanceMetricsStore\n",
        "import com.notcan.app.localai.GemmaRuntimeCache\nimport com.notcan.app.performance.PerformanceMetricsStore\n")
replace(settings,
        "    var runtime by remember { mutableStateOf<PerformanceMetricsStore.RuntimeSnapshot?>(null) }\n",
        "    var runtime by remember { mutableStateOf<PerformanceMetricsStore.RuntimeSnapshot?>(null) }\n    var gemmaAccelerationBytes by remember { mutableStateOf(0L) }\n    var gemmaAccelerationFiles by remember { mutableStateOf(0) }\n")
replace(settings,
        "        runtime = data.third\n",
        "        runtime = data.third\n        gemmaAccelerationBytes = withContext(Dispatchers.IO) { GemmaRuntimeCache.sizeBytes(context) }\n        gemmaAccelerationFiles = withContext(Dispatchers.IO) { GemmaRuntimeCache.fileCount(context) }\n")
replace(settings,
        "            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                Button(\n",
        "            if (gemmaAccelerationBytes > 0L) {\n                Text(\n                    \"Aceleración local de Gemma: ${formatBytes(gemmaAccelerationBytes)} · $gemmaAccelerationFiles archivo(s) persistentes\",\n                    color = NotCanBlue,\n                    style = MaterialTheme.typography.bodySmall\n                )\n                Text(\n                    \"Se conserva fuera de la caché temporal porque LiteRT la reutiliza para evitar recompilar el motor en cada sesión.\",\n                    color = NotCanGray,\n                    style = MaterialTheme.typography.bodySmall\n                )\n            }\n\n            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                Button(\n")
replace(settings,
        "                    val backend = m.gemmaBackend.ifBlank { \"motor local\" }\n                    MetricLine(\"Gemma · carga del motor\", \"${formatTimeMetric(m.gemmaLoadMs)} · $backend\")\n                    MetricLine(\"Gemma · primer token\", formatTimeMetric(m.gemmaFirstTokenMs))\n",
        "                    val loadBackend = m.gemmaLoadBackend.ifBlank { m.gemmaBackend.ifBlank { \"motor local\" } }\n                    MetricLine(\"Gemma · carga del motor\", \"${formatTimeMetric(m.gemmaLoadMs)} · $loadBackend\")\n                    if (m.gemmaTotalMs > 0L && m.gemmaBackend.isNotBlank()) {\n                        MetricLine(\"Gemma · backend de respuesta\", m.gemmaBackend)\n                    }\n                    if (m.gemmaFallbackReason.isNotBlank()) {\n                        MetricLine(\"Gemma · último cambio de backend\", m.gemmaFallbackReason)\n                    }\n                    MetricLine(\"Gemma · primer token\", formatTimeMetric(m.gemmaFirstTokenMs))\n")
replace(settings,
        "                \"La limpieza manual conserva archivos recientes para no interrumpir una transcripción o una tarea en curso. Gemma, Whisper, Moonshine, grabaciones, documentos y base de datos quedan fuera de esta limpieza.\",\n",
        "                \"La limpieza manual conserva archivos recientes y no borra la aceleración persistente de Gemma, modelos, grabaciones, documentos ni base de datos. El motor local libera su RAM tras 10 min sin uso.\",\n")

service = "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
replace(service,
        "    fun startNewConversation() {\n        preferences.mistralConversationId = \"\"\n    }\n\n    suspend fun studyAssistant(\n",
        "    fun startNewConversation() {\n        preferences.mistralConversationId = \"\"\n    }\n\n    suspend fun warmLocalGemmaIfSelected(): String? {\n        if (preferences.aiEnginePreference != \"Gemma 4 local\") return null\n        return localGemma.warmUp()\n    }\n\n    suspend fun studyAssistant(\n")

vm = "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt"
replace(vm,
        "    fun openSubjects() {\n        _selectedSubjectId.value = null\n        _selectedClassId.value = null\n        _selectedNoteId.value = null\n    }\n\n    fun selectSubject(id: String) {\n",
        "    fun openSubjects() {\n        _selectedSubjectId.value = null\n        _selectedClassId.value = null\n        _selectedNoteId.value = null\n    }\n\n    fun warmLocalAiIfUseful() {\n        if (_aiBusy.value) return\n        viewModelScope.launch(Dispatchers.IO) { runCatching { aiService.warmLocalGemmaIfSelected() } }\n    }\n\n    fun selectSubject(id: String) {\n")

main = "app/src/main/java/com/notcan/app/MainActivity.kt"
replace(main,
        "                    onPageChanged = { rootPage = it },\n",
        "                    onPageChanged = { page ->\n                        rootPage = page\n                        if (page == 5) studyViewModel.warmLocalAiIfUseful()\n                    },\n")

build = "app/build.gradle.kts"
replace(build, '        versionCode = 53\n        versionName = "0.8.30"\n', '        versionCode = 54\n        versionName = "0.8.31"\n')

workflow = ".github/workflows/android-debug.yml"
replace(workflow, "notcan-v0.8.30-performance-metrics-apk", "notcan-v0.8.31-adaptive-performance-apk")
replace(workflow, "v0.8.30-test", "v0.8.31-test")
replace(workflow, "NotCan-v0.8.30.apk", "NotCan-v0.8.31.apk")
replace(workflow, "NotCan v0.8.30 · métricas de rendimiento reales", "NotCan v0.8.31 · Gemma más eficiente")
replace(workflow,
        "Build v0.8.30. Añade mediciones locales de arranque, apertura de materias, carga de Gemma, primer token, tiempo total y velocidad estimada, RAM PSS/heap, estado térmico Android y tiempos de transcripción. Whisper local separa conversión, carga del modelo e inferencia para localizar cuellos de botella. No envía telemetría ni contenido fuera del dispositivo. Mantiene Baseline/Startup Profiles, caché controlada y Gemma 4 sobre LiteRT-LM 0.11.0.",
        "Build v0.8.31. Conserva los artefactos de aceleración de LiteRT fuera de la caché temporal, precarga Gemma al abrir TuNot cuando está seleccionado, reduce el contexto de preguntas focalizadas y libera el motor tras 10 minutos sin uso. Las métricas distinguen backend de carga y respuesta para detectar fallback GPU/CPU. Mantiene Groq/Whisper, Baseline/Startup Profiles y LiteRT-LM 0.11.0.")

print("v0.8.31 tuning applied")
