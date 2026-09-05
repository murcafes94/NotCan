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
        "        check(isAvailable()) { \"Gemma 4 LiteRT-LM no está instalado\" }\n\n        val subjectKey",
        "        check(isAvailable()) { \"Gemma 4 LiteRT-LM no está instalado\" }\n        idleReleaseJob?.cancel()\n\n        val subjectKey")
replace(engine,
        "        lastAnswerText = answer.text\n        lastAnswerSubject = subjectKey\n        answer\n",
        "        lastAnswerText = answer.text\n        lastAnswerSubject = subjectKey\n        scheduleIdleRelease()\n        answer\n")
replace(engine,
        "    private suspend fun resetEngine() {\n",
        "    private fun scheduleIdleRelease() {\n        idleReleaseJob?.cancel()\n        idleReleaseJob = engineScope.launch {\n            delay(ENGINE_IDLE_RELEASE_MS)\n            mutex.withLock { resetEngine() }\n        }\n    }\n\n    private suspend fun resetEngine() {\n")
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
replace(engine,
        "        if (isBroadSourceRequest(question)) return \"Extensión: desarrolla el recurso o resumen con la amplitud necesaria para cubrir bien el material, evitando solo la repetición.\"\n",
        "        if (isBroadSourceRequest(question)) return \"Extensión: desarrolla el recurso o resumen con la amplitud necesaria para cubrir bien el material, evitando solo la repetición.\"\n        val simpleDefinition = n.length <= 100 && listOf(\"que es \", \"define \", \"explica el \", \"explica la \").any(n::startsWith)\n        if (simpleDefinition) return \"Extensión: responde con una explicación clara y completa en 2–4 párrafos breves; evita convertir una pregunta puntual en un ensayo.\"\n")

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
        "                \"La limpieza manual conserva archivos recientes para no interrumpir una transcripción o una tarea en curso. Gemma, Whisper, Moonshine, grabaciones, documentos y base de datos quedan fuera de esta limpieza.\",\n",
        "                \"La limpieza manual conserva archivos recientes y no borra la aceleración persistente de Gemma, modelos, grabaciones, documentos ni base de datos. El motor local libera su RAM tras 10 min sin uso.\",\n")

build = "app/build.gradle.kts"
replace(build, '        versionCode = 53\n        versionName = "0.8.30"\n', '        versionCode = 54\n        versionName = "0.8.31"\n')

workflow = ".github/workflows/android-debug.yml"
replace(workflow, "notcan-v0.8.30-performance-metrics-apk", "notcan-v0.8.31-adaptive-performance-apk")
replace(workflow, "v0.8.30-test", "v0.8.31-test")
replace(workflow, "NotCan-v0.8.30.apk", "NotCan-v0.8.31.apk")
replace(workflow, "NotCan v0.8.30 · métricas de rendimiento reales", "NotCan v0.8.31 · Gemma más eficiente")
replace(workflow,
        "Build v0.8.30. Añade mediciones locales de arranque, apertura de materias, carga de Gemma, primer token, tiempo total y velocidad estimada, RAM PSS/heap, estado térmico Android y tiempos de transcripción. Whisper local separa conversión, carga del modelo e inferencia para localizar cuellos de botella. No envía telemetría ni contenido fuera del dispositivo. Mantiene Baseline/Startup Profiles, caché controlada y Gemma 4 sobre LiteRT-LM 0.11.0.",
        "Build v0.8.31. Conserva los artefactos de aceleración de LiteRT fuera de la caché temporal para evitar recompilaciones, reduce el contexto de preguntas focalizadas, acorta respuestas simples y libera Gemma tras 10 minutos sin uso para recuperar RAM. Mantiene métricas locales, Baseline/Startup Profiles, Groq/Whisper y Gemma 4 sobre LiteRT-LM 0.11.0.")

print("v0.8.31 tuning applied")
