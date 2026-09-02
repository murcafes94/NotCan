from pathlib import Path

# -----------------------------------------------------------------------------
# Preferences: deterministic TuNot engine selection + last LFM diagnostic.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/settings/NotCanPreferences.kt')
s = p.read_text()
anchor = '''    var aiDetail: String
        get() = prefs.getString(KEY_AI_DETAIL, "Equilibrado") ?: "Equilibrado"
        set(value) = prefs.edit().putString(KEY_AI_DETAIL, value).apply()
'''
insert = anchor + '''
    var aiEnginePreference: String
        get() = prefs.getString(KEY_AI_ENGINE, "Automático") ?: "Automático"
        set(value) = prefs.edit().putString(KEY_AI_ENGINE, value).apply()

    var lastLfmError: String
        get() = prefs.getString(KEY_LAST_LFM_ERROR, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_LFM_ERROR, value.take(500)).apply()
'''
if 'var aiEnginePreference' not in s:
    if anchor not in s: raise SystemExit('prefs aiDetail anchor missing')
    s = s.replace(anchor, insert)
const_anchor = '        private const val KEY_AI_DETAIL = "ai_detail"\n'
if 'KEY_AI_ENGINE' not in s:
    s = s.replace(const_anchor, const_anchor + '        private const val KEY_AI_ENGINE = "ai_engine_preference"\n        private const val KEY_LAST_LFM_ERROR = "last_lfm_error"\n')
p.write_text(s)

# -----------------------------------------------------------------------------
# LFM engine: longer cold-start budget and safer output handling.
# -----------------------------------------------------------------------------
Path('app/src/main/java/com/notcan/app/ai/LocalLfmTuNotEngine.kt').write_text(r'''package com.notcan.app.ai

import android.content.Context
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.notcan.app.localai.StudyModelManager
import com.notcan.app.localai.StudyModelState
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/** Experimental on-device TuNot brain backed by LiquidAI LFM2.5 + llama.cpp. */
class LocalLfmTuNotEngine(context: Context) {
    private val appContext = context.applicationContext
    private val modelManager = StudyModelManager(appContext)
    private val mutex = Mutex()

    fun isAvailable(): Boolean = runCatching {
        modelManager.state() == StudyModelState.INSTALLED
    }.getOrDefault(false)

    suspend fun answer(
        subjectName: String?,
        notes: String,
        transcript: String,
        question: String,
        strictSources: Boolean
    ): String = mutex.withLock {
        check(isAvailable()) { "LFM2.5 no está instalado" }
        val engine = AiChat.getInferenceEngine(appContext)
        ensureModelReady(engine)

        val sourceContext = buildString {
            subjectName?.takeIf { it.isNotBlank() }?.let { appendLine("Materia: $it") }
            if (notes.isNotBlank()) {
                appendLine("\n[Apuntes]")
                appendLine(notes.takeLast(MAX_SOURCE_PART_CHARS))
            }
            if (transcript.isNotBlank()) {
                appendLine("\n[Transcripción]")
                appendLine(transcript.takeLast(MAX_SOURCE_PART_CHARS))
            }
        }.takeLast(MAX_SOURCE_CHARS)

        val prompt = buildString {
            if (strictSources) {
                appendLine("Modo SOLO MIS FUENTES: responde únicamente con el material incluido. Si no consta, dilo claramente.")
            } else {
                appendLine("Usa el material de clase cuando sea pertinente. Si respondes con conocimiento general, no lo presentes como cita de los apuntes.")
            }
            if (sourceContext.isNotBlank()) {
                appendLine("\n--- MATERIAL DE NOTCAN ---")
                appendLine(sourceContext)
                appendLine("--- FIN DEL MATERIAL ---")
            }
            appendLine("\nPregunta del estudiante:")
            append(question.trim())
        }

        val output = StringBuilder()
        withTimeout(GENERATION_TIMEOUT_MS) {
            engine.sendUserPrompt(prompt, PREDICT_TOKENS).collect { token -> output.append(token) }
        }
        sanitizeModelOutput(output.toString())
            .ifBlank { error("LFM2.5 no produjo texto utilizable") }
    }

    private fun sanitizeModelOutput(raw: String): String = raw
        .replace(SPECIAL_TOKEN_REGEX, "")
        .replace("</pad/>", "")
        .replace("</pad>", "")
        .replace("<pad>", "")
        .trim()

    private suspend fun ensureModelReady(engine: InferenceEngine) {
        var state = withTimeout(INIT_TIMEOUT_MS) {
            engine.state.first {
                it is InferenceEngine.State.Initialized ||
                    it is InferenceEngine.State.ModelReady ||
                    it is InferenceEngine.State.Error
            }
        }
        if (state is InferenceEngine.State.Error) {
            runCatching { engine.cleanUp() }
            state = withTimeout(INIT_TIMEOUT_MS) {
                engine.state.first { it is InferenceEngine.State.Initialized || it is InferenceEngine.State.ModelReady }
            }
        }
        if (state is InferenceEngine.State.ModelReady) return
        engine.loadModel(modelManager.modelFile().absolutePath)
        engine.setSystemPrompt(SYSTEM_PROMPT)
    }

    companion object {
        const val MODEL_LABEL = "LFM2.5 1.2B · local"
        private const val MAX_SOURCE_PART_CHARS = 3_600
        private const val MAX_SOURCE_CHARS = 7_200
        private const val PREDICT_TOKENS = 512
        private const val INIT_TIMEOUT_MS = 120_000L
        private const val GENERATION_TIMEOUT_MS = 180_000L
        private val SPECIAL_TOKEN_REGEX = Regex("""<\|[^>]+\|>""")
        private val SYSTEM_PROMPT = """
            Eres TuNot, tutor académico de NotCan ejecutándose completamente en el dispositivo.
            Responde en español claro, preciso y útil para estudiar. Prioriza fidelidad a los apuntes y transcripciones proporcionados.
            No inventes citas, páginas, autores, fechas ni afirmaciones ausentes de una fuente cuando el usuario pida trabajar solo con sus fuentes.
            Distingue una explicación general de una afirmación tomada del material del estudiante. No muestres cadena de pensamiento.
            Para teología católica, distingue enseñanza oficial, disciplina, opinión teológica e interpretación académica; no presentes una opinión como magisterio.
            No llames herramientas ni emitas tokens especiales. Responde con texto normal y Markdown sencillo.
        """.trimIndent()
    }
}
''')

# -----------------------------------------------------------------------------
# Native bridge: for LFM2/LFM2.5 use the exact ChatML-like framing documented
# by Liquid instead of relying on the generic Android sample's incremental Jinja
# formatter. Keep generic behavior untouched for future non-LFM models.
# -----------------------------------------------------------------------------
Path('llama-android/src/main/cpp/CMakeLists.txt').write_text(r'''cmake_minimum_required(VERSION 3.31.5)

project("ai-chat" VERSION 1.0.0 LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED true)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED true)

if(DEFINED ANDROID_ABI)
    message(STATUS "Detected Android ABI: ${ANDROID_ABI}")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_SYSTEM_ARCH "ARM")
        set(GGML_CPU_KLEIDIAI OFF)
        set(GGML_OPENMP OFF)
    else()
        message(FATAL_ERROR "Unsupported ABI: ${ANDROID_ABI}")
    endif()
endif()

set(LLAMA_SRC ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp)
add_subdirectory(${LLAMA_SRC} build-llama)
if(NOT TARGET common)
    add_subdirectory(${LLAMA_SRC}/common build-common)
endif()

set(UPSTREAM_AI_CHAT ${LLAMA_SRC}/examples/llama.android/lib/src/main/cpp/ai_chat.cpp)
file(READ ${UPSTREAM_AI_CHAT} AI_CHAT_SOURCE)
string(REPLACE "#include \"logging.h\"" "#include \"logging_compat.h\"" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE "constexpr int   DEFAULT_CONTEXT_SIZE    = 8192;" "constexpr int   DEFAULT_CONTEXT_SIZE    = 4096;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE "constexpr int   BATCH_SIZE              = 512;" "constexpr int   BATCH_SIZE              = 256;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE "constexpr float DEFAULT_SAMPLER_TEMP    = 0.3f;" "constexpr float DEFAULT_SAMPLER_TEMP    = 0.1f;" AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

# Detect LFM by the model description once loaded.
string(REPLACE
    "static common_sampler                   * g_sampler;"
    "static common_sampler                   * g_sampler;\nstatic bool                              g_notcan_lfm = false;"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE
    "    g_model = model;\n    return 0;"
    "    g_model = model;\n    char notcan_model_desc[256] = {};\n    llama_model_desc(g_model, notcan_model_desc, sizeof(notcan_model_desc));\n    std::string notcan_desc(notcan_model_desc);\n    g_notcan_lfm = notcan_desc.find(\"LFM2\") != std::string::npos || notcan_desc.find(\"lfm2\") != std::string::npos;\n    LOGi(\"NotCan model mode: %s · %s\", g_notcan_lfm ? \"LFM ChatML\" : \"generic template\", notcan_model_desc);\n    return 0;"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

# Do not apply the incremental generic formatter to LFM. NotCan adds the exact
# documented ChatML framing below and asks the tokenizer to parse special tokens.
string(REPLACE
    "const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get());"
    "const bool has_chat_template = common_chat_templates_was_explicit(g_chat_templates.get()) && !g_notcan_lfm;"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

string(REPLACE
    "    std::string formatted_system_prompt(system_prompt);\n    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);"
    "    std::string formatted_system_prompt(system_prompt);\n    env->ReleaseStringUTFChars(jsystem_prompt, system_prompt);\n    if (g_notcan_lfm) {\n        formatted_system_prompt = std::string(\"<|startoftext|><|im_start|>system\\n\") + formatted_system_prompt + \"<|im_end|>\\n\";\n    }"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE
    "const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,\n                                               has_chat_template, has_chat_template);"
    "const auto system_tokens = common_tokenize(g_context, formatted_system_prompt,\n                                               has_chat_template, has_chat_template || g_notcan_lfm);"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

string(REPLACE
    "    std::string formatted_user_prompt(user_prompt);\n    env->ReleaseStringUTFChars(juser_prompt, user_prompt);"
    "    std::string formatted_user_prompt(user_prompt);\n    env->ReleaseStringUTFChars(juser_prompt, user_prompt);\n    if (g_notcan_lfm) {\n        formatted_user_prompt = std::string(\"<|im_start|>user\\n\") + formatted_user_prompt + \"<|im_end|>\\n<|im_start|>assistant\\n\";\n    }"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE
    "auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template);"
    "auto user_tokens = common_tokenize(g_context, formatted_user_prompt, has_chat_template, has_chat_template || g_notcan_lfm);"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

# LFM control tokens are protocol, never user-visible content. The model should
# normally terminate with EOG, but stopping on a leaked control token is safer.
string(REPLACE
    "    auto new_token_chars = common_token_to_piece(g_context, new_token_id);\n    cached_token_chars += new_token_chars;"
    "    auto new_token_chars = common_token_to_piece(g_context, new_token_id);\n    if (g_notcan_lfm && new_token_chars.rfind(\"<|\", 0) == 0) {\n        LOGw(\"NotCan stopped leaked LFM control token: %s\", new_token_chars.c_str());\n        return nullptr;\n    }\n    cached_token_chars += new_token_chars;"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")
string(REPLACE
    "        chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());"
    "        if (!g_notcan_lfm) chat_add_and_format(ROLE_ASSISTANT, assistant_ss.str());"
    AI_CHAT_SOURCE "${AI_CHAT_SOURCE}")

set(PATCHED_AI_CHAT ${CMAKE_CURRENT_BINARY_DIR}/ai_chat_compat.cpp)
file(WRITE ${PATCHED_AI_CHAT} "${AI_CHAT_SOURCE}")

add_library(${CMAKE_PROJECT_NAME} SHARED ${PATCHED_AI_CHAT})

target_compile_definitions(${CMAKE_PROJECT_NAME} PRIVATE
        GGML_SYSTEM_ARCH=${GGML_SYSTEM_ARCH}
        GGML_CPU_KLEIDIAI=$<BOOL:${GGML_CPU_KLEIDIAI}>
        GGML_OPENMP=$<BOOL:${GGML_OPENMP}>)

target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_LIST_DIR}
        ${LLAMA_SRC}/examples/llama.android/lib/src/main/cpp
        ${LLAMA_SRC}
        ${LLAMA_SRC}/common
        ${LLAMA_SRC}/include
        ${LLAMA_SRC}/ggml/include
        ${LLAMA_SRC}/ggml/src
        ${LLAMA_SRC}/vendor)

target_link_libraries(${CMAKE_PROJECT_NAME}
        llama
        common
        android
        log)
''')

# -----------------------------------------------------------------------------
# Service: deterministic engine preference + response provenance + diagnostics.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ai/NotCanAiService.kt')
s = p.read_text()
old = '''        suspend fun localFallback(): String {
            val lfmEligible = !mapRequest && !flashcardRequest && !quizRequest
            if (lfmEligible && localLfm.isAvailable()) {
                try {
                    return localLfm.answer(
                        subjectName = subjectName,
                        notes = plainNotes,
                        transcript = plainTranscript,
                        question = localQuestion,
                        strictSources = strictSources
                    )
                } catch (_: Throwable) {
                    // El motor extractivo permanece como red de seguridad si llama.cpp falla.
                }
            }
            return OfflineTuNotEngine.answer(
                subjectName = subjectName,
                notes = plainNotes,
                transcript = plainTranscript,
                question = localQuestion
            )
        }

        if (!isConfigured()) return localFallback()
'''
new = '''        suspend fun localFallback(skipLfm: Boolean = false): String {
            val lfmEligible = !mapRequest && !flashcardRequest && !quizRequest
            if (!skipLfm && lfmEligible && localLfm.isAvailable()) {
                try {
                    val answer = localLfm.answer(
                        subjectName = subjectName,
                        notes = plainNotes,
                        transcript = plainTranscript,
                        question = localQuestion,
                        strictSources = strictSources
                    )
                    preferences.lastLfmError = ""
                    return markEngine("LFM2.5 local", answer)
                } catch (t: Throwable) {
                    preferences.lastLfmError = t.message ?: t.javaClass.simpleName
                }
            }
            val basic = OfflineTuNotEngine.answer(
                subjectName = subjectName,
                notes = plainNotes,
                transcript = plainTranscript,
                question = localQuestion
            )
            return markEngine("Local básico", basic)
        }

        when (preferences.aiEnginePreference) {
            "LFM2.5 local" -> return localFallback(skipLfm = false)
            "Local básico" -> return localFallback(skipLfm = true)
        }

        if (!isConfigured()) return localFallback()
'''
if old not in s: raise SystemExit('local fallback block missing')
s = s.replace(old,new)
old = '''        return try {
            sendToMistral(prompt)
        } catch (_: Throwable) {
            localFallback()
        }
'''
new = '''        return try {
            markEngine("Mistral", sendToMistral(prompt))
        } catch (_: Throwable) {
            localFallback()
        }
'''
if old not in s: raise SystemExit('mistral return block missing')
s = s.replace(old,new)
# Add marker helper before sendToMistral.
anchor = '    private fun sendToMistral(prompt: String): String {\n'
if 'private fun markEngine' not in s:
    if anchor not in s: raise SystemExit('sendToMistral anchor missing')
    s = s.replace(anchor, '''    private fun markEngine(engine: String, text: String): String =
        "<<<NOTCAN_ENGINE:${engine.replace(\">\", \"\")}>>>\\n$text"

''' + anchor)
p.write_text(s)

# -----------------------------------------------------------------------------
# Chat UI: each answer reports the engine that actually produced it.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/ai/NotCanAiScreen.kt')
s = p.read_text()
old = '''private data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val rawContent: String = content,
    val mapArtifact: ParsedStudyMapArtifact? = null,
    val flashcards: ParsedFlashcardArtifact? = null,
    val quizArtifact: ParsedQuizArtifact? = null
)

private fun messageFromRaw(role: ChatRole, raw: String): ChatMessage {
    if (role == ChatRole.USER) return ChatMessage(role, raw, raw)
    val map = StudyMapArtifactParser.parse(raw)
    val deck = StudyFlashcardArtifactParser.parse(raw)
    val quiz = StudyQuizArtifactParser.parse(raw)
    val visible = when {
        map != null -> StudyMapArtifactParser.stripArtifact(raw)
        deck != null -> StudyFlashcardArtifactParser.stripArtifact(raw)
        quiz != null -> StudyQuizArtifactParser.stripArtifact(raw)
        else -> sanitizeUnparsedArtifact(raw)
    }
    return ChatMessage(role, visible, raw, map, deck, quiz)
}
'''
new = '''private data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val rawContent: String = content,
    val mapArtifact: ParsedStudyMapArtifact? = null,
    val flashcards: ParsedFlashcardArtifact? = null,
    val quizArtifact: ParsedQuizArtifact? = null,
    val engineLabel: String? = null
)

private val engineMarkerRegex = Regex("""^<<<NOTCAN_ENGINE:([^>]+)>>>\\s*""")

private fun messageFromRaw(role: ChatRole, raw: String): ChatMessage {
    if (role == ChatRole.USER) return ChatMessage(role, raw, raw)
    val engineMatch = engineMarkerRegex.find(raw)
    val engineLabel = engineMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    val cleanRaw = engineMatch?.let { raw.removeRange(it.range) } ?: raw
    val map = StudyMapArtifactParser.parse(cleanRaw)
    val deck = StudyFlashcardArtifactParser.parse(cleanRaw)
    val quiz = StudyQuizArtifactParser.parse(cleanRaw)
    val visible = when {
        map != null -> StudyMapArtifactParser.stripArtifact(cleanRaw)
        deck != null -> StudyFlashcardArtifactParser.stripArtifact(cleanRaw)
        quiz != null -> StudyQuizArtifactParser.stripArtifact(cleanRaw)
        else -> sanitizeUnparsedArtifact(cleanRaw)
    }
    return ChatMessage(role, visible, raw, map, deck, quiz, engineLabel)
}
'''
if old not in s: raise SystemExit('ChatMessage block missing')
s = s.replace(old,new)
old = '            CompactChatHeader(subjectName, classTitle, configured, toolsOpen) { toolsOpen = !toolsOpen }\n'
new = '''            val actualEngine = messages.lastOrNull { it.role == ChatRole.ASSISTANT }?.engineLabel
                ?: if (configured) "Automático" else "Local"
            CompactChatHeader(subjectName, classTitle, actualEngine, toolsOpen) { toolsOpen = !toolsOpen }
'''
if old not in s: raise SystemExit('CompactChatHeader call missing')
s = s.replace(old,new)
old = '''private fun CompactChatHeader(subjectName: String?, classTitle: String?, configured: Boolean, toolsOpen: Boolean, onToggleTools: () -> Unit) {
'''
new = '''private fun CompactChatHeader(subjectName: String?, classTitle: String?, engineLabel: String, toolsOpen: Boolean, onToggleTools: () -> Unit) {
'''
if old not in s: raise SystemExit('header signature missing')
s = s.replace(old,new)
s = s.replace('        ConnectionBadge(configured)\n', '        ConnectionBadge(engineLabel)\n')
old = '''private fun ConnectionBadge(configured: Boolean) {
    Surface(color = NotCanBlue.copy(alpha = if (configured) 0.13f else 0.09f), shape = RoundedCornerShape(50)) {
        Text(
            if (configured) "Mistral" else "Local",
            color = if (configured) NotCanBlue else NotCanGray,
'''
new = '''private fun ConnectionBadge(engineLabel: String) {
    val emphasized = engineLabel.contains("Mistral") || engineLabel.contains("LFM2.5")
    Surface(color = NotCanBlue.copy(alpha = if (emphasized) 0.13f else 0.09f), shape = RoundedCornerShape(50)) {
        Text(
            engineLabel,
            color = if (emphasized) NotCanBlue else NotCanGray,
'''
if old not in s: raise SystemExit('ConnectionBadge block missing')
s = s.replace(old,new)
old = '''                Text(if (user) "Tú" else "TuNot", color = if (user) NotCanBlue else NotCanGray, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
'''
new = '''                Text(
                    if (user) "Tú" else buildString {
                        append("TuNot")
                        message.engineLabel?.let { append(" · $it") }
                    },
                    color = if (user) NotCanBlue else NotCanGray,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
'''
if old not in s: raise SystemExit('bubble label missing')
s = s.replace(old,new)
p.write_text(s)

# -----------------------------------------------------------------------------
# Settings: explicit engine chooser and visible last LFM diagnostic.
# -----------------------------------------------------------------------------
p = Path('app/src/main/java/com/notcan/app/ui/settings/SettingsScreen.kt')
s = p.read_text()
state_anchor = '    var detail by remember { mutableStateOf(preferences.aiDetail) }\n'
if 'var aiEngine by remember' not in s:
    if state_anchor not in s: raise SystemExit('settings detail state missing')
    s = s.replace(state_anchor, state_anchor + '    var aiEngine by remember { mutableStateOf(preferences.aiEnginePreference) }\n')
insert_before = '''        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = NotCanBlue)
'''
engine_card = '''        Card(colors = CardDefaults.cardColors(containerColor = NotCanSurface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NotCanBlue)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Motor de TuNot", color = NotCanOffWhite, fontWeight = FontWeight.SemiBold)
                        Text("Elige Automático o fuerza un motor para probarlo.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Automático", "Mistral").forEach { option ->
                        FilterChip(
                            selected = aiEngine == option,
                            onClick = { aiEngine = option; preferences.aiEnginePreference = option },
                            label = { Text(option) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("LFM2.5 local", "Local básico").forEach { option ->
                        FilterChip(
                            selected = aiEngine == option,
                            onClick = { aiEngine = option; preferences.aiEnginePreference = option },
                            label = { Text(option) }
                        )
                    }
                }
                if (aiEngine == "LFM2.5 local" && studyState != StudyModelState.INSTALLED) {
                    Text("LFM2.5 todavía no está instalado.", color = NotCanGray, style = MaterialTheme.typography.bodySmall)
                }
                if (preferences.lastLfmError.isNotBlank()) {
                    Text("Último fallo de LFM2.5: ${preferences.lastLfmError}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

'''
if 'Text("Motor de TuNot"' not in s:
    if insert_before not in s: raise SystemExit('Mistral card anchor missing')
    s = s.replace(insert_before, engine_card + insert_before, 1)
p.write_text(s)

# -----------------------------------------------------------------------------
# Version and release metadata.
# -----------------------------------------------------------------------------
p = Path('app/build.gradle.kts')
s = p.read_text().replace('versionCode = 40', 'versionCode = 41').replace('versionName = "0.8.18.1"', 'versionName = "0.8.18.2"')
p.write_text(s)

p = Path('.github/workflows/android-debug.yml')
s = p.read_text()
s = s.replace('notcan-v0.8.18.1-lfm25-chat-fix-apk', 'notcan-v0.8.18.2-lfm25-runtime-apk')
s = s.replace('TAG="v0.8.18.1-lfm-test"', 'TAG="v0.8.18.2-lfm-test"')
s = s.replace('NotCan-v0.8.18.1-LFM2.5-chat-fix.apk', 'NotCan-v0.8.18.2-LFM2.5-runtime.apk')
s = s.replace('NotCan v0.8.18.1 · TuNot LFM2.5 chat fix', 'NotCan v0.8.18.2 · TuNot LFM2.5 runtime')
s = s.replace('Corrige la plantilla de chat', 'Corrige el runtime ChatML y añade selección real de motor')
p.write_text(s)

print('LFM2.5 runtime v0.8.18.2 patch applied')
