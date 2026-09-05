from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one match in {path}, found {count}: {old[:80]!r}")
    p.write_text(text.replace(old, new, 1), encoding="utf-8")


ENGINE = "app/src/main/java/com/notcan/app/ai/LiteRtGemmaTuNotEngine.kt"
SERVICE = "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
VM = "app/src/main/java/com/notcan/app/ui/NotCanViewModel.kt"
GRADLE = "app/build.gradle.kts"

# 1) Do not parse the 2.6 GB LiteRT-LM package for capabilities on every cold engine start.
replace_once(
    ENGINE,
    '    private val preferences = NotCanPreferences(appContext)\n    private val mutex = Mutex()\n',
    '    private val preferences = NotCanPreferences(appContext)\n'
    '    private val runtimePrefs = appContext.getSharedPreferences("notcan_gemma_runtime", Context.MODE_PRIVATE)\n'
    '    private val mutex = Mutex()\n'
)

replace_once(
    ENGINE,
    '''            val modelPath = modelManager.modelFile().absolutePath
            val gpuAttempt = runCatching {
                var canUseSpeculative = false
                runCatching {
                    Capabilities(modelPath).use { capabilities ->
                        canUseSpeculative = capabilities.hasSpeculativeDecodingSupport()
                    }
                }
                ExperimentalFlags.enableSpeculativeDecoding = canUseSpeculative
''',
    '''            val modelPath = modelManager.modelFile().absolutePath
            val gpuAttempt = runCatching {
                val canUseSpeculative = speculativeDecodingSupported(modelPath)
                ExperimentalFlags.enableSpeculativeDecoding = canUseSpeculative
'''
)

replace_once(
    ENGINE,
    '''    @OptIn(ExperimentalApi::class)
    private suspend fun ensureEngineReady(): EngineHolder {
''',
    '''    private fun speculativeDecodingSupported(modelPath: String): Boolean {
        val model = modelManager.modelFile()
        val fingerprint = "${model.length()}:${model.lastModified()}"
        val cachedFingerprint = runtimePrefs.getString(KEY_CAPABILITIES_FINGERPRINT, null)
        if (
            cachedFingerprint == fingerprint &&
            runtimePrefs.contains(KEY_SPECULATIVE_DECODING_SUPPORTED)
        ) {
            return runtimePrefs.getBoolean(KEY_SPECULATIVE_DECODING_SUPPORTED, false)
        }

        var supported = false
        val probeSucceeded = runCatching {
            Capabilities(modelPath).use { capabilities ->
                supported = capabilities.hasSpeculativeDecodingSupport()
            }
        }.isSuccess
        if (probeSucceeded) {
            runtimePrefs.edit()
                .putString(KEY_CAPABILITIES_FINGERPRINT, fingerprint)
                .putBoolean(KEY_SPECULATIVE_DECODING_SUPPORTED, supported)
                .apply()
        }
        return supported
    }

    @OptIn(ExperimentalApi::class)
    private suspend fun ensureEngineReady(): EngineHolder {
'''
)

# 2) Tighten RAG/web prompt budgets for ordinary questions. Broad/deep requests retain larger budgets.
for old, new in [
    ('private const val MAX_WEB_CONTEXT_CHARS = 6_000', 'private const val MAX_WEB_CONTEXT_CHARS = 4_200'),
    ('private const val MAX_VOCAB_CONTEXT_CHARS = 1_000', 'private const val MAX_VOCAB_CONTEXT_CHARS = 700'),
    ('private const val MAX_BROAD_SOURCE_CHARS = 8_500', 'private const val MAX_BROAD_SOURCE_CHARS = 6_500'),
    ('private const val MAX_ARTIFACT_SOURCE_CHARS = 5_200', 'private const val MAX_ARTIFACT_SOURCE_CHARS = 4_800'),
    ('private const val MAX_OVERVIEW_SOURCE_CHARS = 5_500', 'private const val MAX_OVERVIEW_SOURCE_CHARS = 4_200'),
    ('private const val MAX_FOCUSED_SOURCE_CHARS = 2_400', 'private const val MAX_FOCUSED_SOURCE_CHARS = 1_600'),
    ('private const val SOURCE_CHUNK_CHARS = 1_000', 'private const val SOURCE_CHUNK_CHARS = 800'),
    ('private const val SOURCE_CHUNK_OVERLAP = 160', 'private const val SOURCE_CHUNK_OVERLAP = 120'),
    ('private const val BROAD_SELECTED_CHUNKS = 7', 'private const val BROAD_SELECTED_CHUNKS = 6'),
    ('private const val OVERVIEW_SELECTED_CHUNKS = 5', 'private const val OVERVIEW_SELECTED_CHUNKS = 4'),
]:
    replace_once(ENGINE, old, new)

# Keep normal balanced responses useful but prevent accidental essay-length output.
replace_once(
    ENGINE,
    '            else -> "Extensión preferida: equilibrada. Usa toda la extensión necesaria para explicar bien; sé breve en preguntas simples y desarrolla las complejas."',
    '            else -> "Extensión preferida: equilibrada. En una pregunta normal responde en 2–5 párrafos breves y detente cuando quede resuelta; amplía solo si la complejidad o la petición lo exige."'
)

# Capability cache keys.
replace_once(
    ENGINE,
    '        private const val MIN_USEFUL_PARTIAL_CHARS = 180\n',
    '        private const val MIN_USEFUL_PARTIAL_CHARS = 180\n'
    '        private const val KEY_CAPABILITIES_FINGERPRINT = "capabilities_fingerprint"\n'
    '        private const val KEY_SPECULATIVE_DECODING_SUPPORTED = "speculative_decoding_supported"\n'
)

# 3) Automatic mode should prewarm Gemma when no online Mistral runtime is configured.
replace_once(
    SERVICE,
    '''    suspend fun warmLocalGemmaIfSelected(): String? {
        if (preferences.aiEnginePreference != "Gemma 4 local") return null
        return localGemma.warmUp()
    }
''',
    '''    suspend fun warmLocalGemmaIfSelected(): String? {
        val preference = preferences.aiEnginePreference
        val shouldWarm = preference == "Gemma 4 local" ||
            (preference == "Automático" && !isConfigured())
        if (!shouldWarm || !localGemma.isAvailable()) return null
        return localGemma.warmUp()
    }
'''
)

# 4) Start engine warm-up while NotCan gathers local notes/indexed sources, then await it before inference.
replace_once(
    VM,
    'import kotlinx.coroutines.Dispatchers\n',
    'import kotlinx.coroutines.Dispatchers\nimport kotlinx.coroutines.async\n'
)
replace_once(
    VM,
    '''        viewModelScope.launch(Dispatchers.IO) {
            try {
                val baseNotes = notePages.value.joinToString("\\n\\n") { "${it.title}\\n${it.body}" }
''',
    '''        viewModelScope.launch(Dispatchers.IO) {
            try {
                val localWarmUp = async { runCatching { aiService.warmLocalGemmaIfSelected() } }
                val baseNotes = notePages.value.joinToString("\\n\\n") { "${it.title}\\n${it.body}" }
'''
)
replace_once(
    VM,
    '''                val finalResult = aiService.studyAssistant(
''',
    '''                localWarmUp.await()
                val finalResult = aiService.studyAssistant(
'''
)

# 5) Version bump.
replace_once(GRADLE, '        versionCode = 54\n        versionName = "0.8.31"', '        versionCode = 55\n        versionName = "0.8.32"')

print("v0.8.32 Gemma latency patch applied")
