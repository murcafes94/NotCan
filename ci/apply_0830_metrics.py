from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace(path: str, old: str, new: str):
    p = ROOT / path
    text = p.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Pattern not found in {path}: {old[:160]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")

# Version.
replace(
    "app/build.gradle.kts",
    'versionCode = 52\n        versionName = "0.8.29"',
    'versionCode = 53\n        versionName = "0.8.30"'
)

# Cold-start-to-first-composition metric.
replace(
    "app/src/main/java/com/notcan/app/MainActivity.kt",
    '''            var rootPage by remember { mutableIntStateOf(0) }\n            NotCanTheme(darkTheme = darkTheme) {''',
    '''            var rootPage by remember { mutableIntStateOf(0) }\n            val performanceMetrics = remember { com.notcan.app.performance.PerformanceMetricsStore(applicationContext) }\n            LaunchedEffect(Unit) {\n                val startupMs = (android.os.SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime()).coerceAtLeast(0L)\n                performanceMetrics.recordStartup(startupMs)\n            }\n            NotCanTheme(darkTheme = darkTheme) {'''
)

# Subject open/data-ready latency.
replace(
    "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt",
    '''    private val whisperModelManager = WhisperModelManager(application)\n    private val localWhisper = LocalWhisperEngine(application)''',
    '''    private val whisperModelManager = WhisperModelManager(application)\n    private val localWhisper = LocalWhisperEngine(application)\n    private val performanceMetrics = com.notcan.app.performance.PerformanceMetricsStore(application)\n    @Volatile private var pendingSubjectOpenStartedAt = 0L'''
)
replace(
    "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt",
    '''        viewModelScope.launch {\n            classes.collect { list ->\n                val selected = _selectedClassId.value''',
    '''        viewModelScope.launch {\n            classes.collect { list ->\n                val pendingOpen = pendingSubjectOpenStartedAt\n                if (pendingOpen > 0L && _selectedSubjectId.value != null) {\n                    performanceMetrics.recordSubjectOpen((android.os.SystemClock.elapsedRealtime() - pendingOpen).coerceAtLeast(0L))\n                    pendingSubjectOpenStartedAt = 0L\n                }\n                val selected = _selectedClassId.value'''
)
replace(
    "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt",
    '    fun selectSubject(id: String) { _selectedSubjectId.value = id; _selectedClassId.value = null; _selectedNoteId.value = null }',
    '''    fun selectSubject(id: String) {\n        pendingSubjectOpenStartedAt = android.os.SystemClock.elapsedRealtime()\n        _selectedSubjectId.value = id\n        _selectedClassId.value = null\n        _selectedNoteId.value = null\n    }'''
)

# Gemma load, first-token and generation metrics; keep LiteRT engine configuration unchanged.
replace(
    "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt",
    '''    private val preferences = NotCanPreferences(appContext)\n    private val mutex = Mutex()''',
    '''    private val preferences = NotCanPreferences(appContext)\n    private val mutex = Mutex()\n    private val performanceMetrics = com.notcan.app.performance.PerformanceMetricsStore(appContext)'''
)
replace(
    "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt",
    '''        val generationTimeoutMs = generationTimeoutMs(intentQuestion)\n        val primaryHolder = ensureEngineReady()''',
    '''        val generationTimeoutMs = generationTimeoutMs(intentQuestion)\n        val engineWasWarm = holder != null\n        val engineLoadStartedAt = SystemClock.elapsedRealtime()\n        val primaryHolder = ensureEngineReady()\n        if (!engineWasWarm) {\n            performanceMetrics.recordGemmaLoad(\n                (SystemClock.elapsedRealtime() - engineLoadStartedAt).coerceAtLeast(0L),\n                primaryHolder.backendLabel\n            )\n        }'''
)
replace(
    "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt",
    '''        val output = StringBuilder()\n        val generationStartedAt = SystemClock.elapsedRealtime()''',
    '''        val output = StringBuilder()\n        val generationStartedAt = SystemClock.elapsedRealtime()\n        var firstTokenMs = 0L'''
)
replace(
    "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt",
    '''                    firstMessage?.toString()?.takeIf { it.isNotEmpty() }?.let { delta ->\n                        output.append(delta)''',
    '''                    firstMessage?.toString()?.takeIf { it.isNotEmpty() }?.let { delta ->\n                        if (firstTokenMs == 0L) firstTokenMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)\n                        output.append(delta)'''
)
replace(
    "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt",
    '''                        if (delta.isNotEmpty()) {\n                            output.append(delta)''',
    '''                        if (delta.isNotEmpty()) {\n                            if (firstTokenMs == 0L) firstTokenMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)\n                            output.append(delta)'''
)
replace(
    "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt",
    '''        Answer(text = text, backendLabel = engineHolder.backendLabel)\n    }''',
    '''        val totalMs = (SystemClock.elapsedRealtime() - generationStartedAt).coerceAtLeast(0L)\n        performanceMetrics.recordGemmaGeneration(\n            backend = engineHolder.backendLabel,\n            firstTokenMs = firstTokenMs.takeIf { it > 0L } ?: totalMs,\n            totalMs = totalMs,\n            outputChars = text.length,\n            promptChars = prompt.length\n        )\n        Answer(text = text, backendLabel = engineHolder.backendLabel)\n    }'''
)

# Whisper local stages: conversion, model load and inference.
replace(
    "app/src/main/java/com/notcan/app/localai/LocalWhisperEngine.kt",
    '''class LocalWhisperEngine(private val context: Context) {\n    private val modelManager = WhisperModelManager(context)''',
    '''class LocalWhisperEngine(private val context: Context) {\n    private val modelManager = WhisperModelManager(context)\n    private val performanceMetrics = com.notcan.app.performance.PerformanceMetricsStore(context.applicationContext)'''
)
replace(
    "app/src/main/java/com/notcan/app/localai/LocalWhisperEngine.kt",
    '''        try {\n            AudioToWavConverter.convert(audio, wav)\n            val model = Whisper.loadModel(context, modelFile.absolutePath)\n            return try {\n                val result = Whisper.transcribe(\n                    model,\n                    wav.absolutePath,\n                    WhisperConfig(language = "es")\n                )''',
    '''        var convertMs = 0L\n        var modelLoadMs = 0L\n        var inferenceMs = 0L\n        try {\n            val convertStartedAt = android.os.SystemClock.elapsedRealtime()\n            AudioToWavConverter.convert(audio, wav)\n            convertMs = (android.os.SystemClock.elapsedRealtime() - convertStartedAt).coerceAtLeast(0L)\n            val modelLoadStartedAt = android.os.SystemClock.elapsedRealtime()\n            val model = Whisper.loadModel(context, modelFile.absolutePath)\n            modelLoadMs = (android.os.SystemClock.elapsedRealtime() - modelLoadStartedAt).coerceAtLeast(0L)\n            return try {\n                val inferenceStartedAt = android.os.SystemClock.elapsedRealtime()\n                val result = Whisper.transcribe(\n                    model,\n                    wav.absolutePath,\n                    WhisperConfig(language = "es")\n                )\n                inferenceMs = (android.os.SystemClock.elapsedRealtime() - inferenceStartedAt).coerceAtLeast(0L)'''
)
replace(
    "app/src/main/java/com/notcan/app/localai/LocalWhisperEngine.kt",
    '''            } finally {\n                Whisper.releaseModel(model)\n            }\n        } finally {''',
    '''            } finally {\n                performanceMetrics.recordLocalWhisperStages(convertMs, modelLoadMs, inferenceMs)\n                Whisper.releaseModel(model)\n            }\n        } finally {'''
)

# End-to-end transcription metric, including provider and approximate audio duration.
replace(
    "app/src/main/java/com/notcan/app/localai/BackgroundTranscriptionWorker.kt",
    '''        val audio = File(path)\n        if (!audio.exists()) return Result.failure(workDataOf(KEY_ERROR to "El audio local ya no existe"))''',
    '''        val audio = File(path)\n        if (!audio.exists()) return Result.failure(workDataOf(KEY_ERROR to "El audio local ya no existe"))\n        val performanceStartedAt = android.os.SystemClock.elapsedRealtime()'''
)
replace(
    "app/src/main/java/com/notcan/app/localai/BackgroundTranscriptionWorker.kt",
    '''            notifyFinished(displayName, academicTerms.isNotEmpty(), provider)\n            Result.success(''',
    '''            val audioDurationMs = transcription.segments.maxOfOrNull { it.endMs } ?: 0L\n            com.notcan.app.performance.PerformanceMetricsStore(applicationContext).recordTranscription(\n                provider = provider,\n                processingMs = (android.os.SystemClock.elapsedRealtime() - performanceStartedAt).coerceAtLeast(0L),\n                audioMs = audioDurationMs\n            )\n            notifyFinished(displayName, academicTerms.isNotEmpty(), provider)\n            Result.success('''
)

# Keep baseline profile aware of the metrics store.
profile = ROOT / "app/src/main/baseline-prof.txt"
text = profile.read_text(encoding="utf-8")
rule = "Lcom/notcan/app/performance/PerformanceMetricsStore;\n"
if rule not in text:
    profile.write_text(text + rule, encoding="utf-8")
