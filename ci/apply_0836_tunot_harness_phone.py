from pathlib import Path

root = Path(__file__).resolve().parents[1]


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text(encoding="utf-8")
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


# 1) TuNot Harness: small native orchestration layer inspired by plugin-based agent harnesses.
harness_path = root / "app/src/main/java/com/notcan/app/ai/harness/TuNotHarness.kt"
harness_path.parent.mkdir(parents=True, exist_ok=True)
harness_path.write_text(r'''package com.notcan.app.ai.harness

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.text.Normalizer
import java.util.Locale

/**
 * Native orchestration core for TuNot.
 *
 * The goal is to keep model selection, tool availability and policy decisions outside the UI and
 * outside individual provider implementations. New model adapters or tools can be registered without
 * turning NotCanAiService into a monolithic router.
 */
enum class TuNotEngine {
    MISTRAL,
    GEMMA,
    LOCAL_BASIC
}

enum class TuNotTool {
    NOTES,
    TRANSCRIPT,
    VOCABULARY,
    WEB_RESEARCH,
    CALENDAR,
    GRADES,
    DOCUMENTS
}

enum class TuNotPolicy {
    CONNECTIVITY,
    SOURCE_ONLY,
    CATHOLIC_ACADEMIC,
    LOCAL_PRIVACY
}

enum class TuNotPromptProfile {
    COMPACT,
    STANDARD,
    ARTIFACT,
    SOURCE_ONLY
}

data class TuNotModelDescriptor(
    val engine: TuNotEngine,
    val label: String,
    val local: Boolean,
    val requiresInternet: Boolean,
    val supportsStreaming: Boolean
)

object TuNotModelRegistry {
    val models: List<TuNotModelDescriptor> = listOf(
        TuNotModelDescriptor(TuNotEngine.MISTRAL, "Mistral", local = false, requiresInternet = true, supportsStreaming = false),
        TuNotModelDescriptor(TuNotEngine.GEMMA, "Gemma 4 local", local = true, requiresInternet = false, supportsStreaming = true),
        TuNotModelDescriptor(TuNotEngine.LOCAL_BASIC, "Local básico", local = true, requiresInternet = false, supportsStreaming = false)
    )

    fun descriptor(engine: TuNotEngine): TuNotModelDescriptor =
        models.first { it.engine == engine }
}

data class TuNotToolDescriptor(
    val tool: TuNotTool,
    val label: String,
    val local: Boolean,
    val implemented: Boolean
)

object TuNotToolRegistry {
    val tools: List<TuNotToolDescriptor> = listOf(
        TuNotToolDescriptor(TuNotTool.NOTES, "Apuntes", local = true, implemented = true),
        TuNotToolDescriptor(TuNotTool.TRANSCRIPT, "Transcripciones", local = true, implemented = true),
        TuNotToolDescriptor(TuNotTool.VOCABULARY, "Vocabulario académico", local = true, implemented = true),
        TuNotToolDescriptor(TuNotTool.WEB_RESEARCH, "Investigación web", local = false, implemented = true),
        TuNotToolDescriptor(TuNotTool.CALENDAR, "Calendario", local = true, implemented = false),
        TuNotToolDescriptor(TuNotTool.GRADES, "Calificaciones", local = true, implemented = false),
        TuNotToolDescriptor(TuNotTool.DOCUMENTS, "Documentos", local = true, implemented = false)
    )
}

data class TuNotExecutionPlan(
    val primaryEngine: TuNotEngine,
    val fallbackChain: List<TuNotEngine>,
    val enabledTools: Set<TuNotTool>,
    val policies: Set<TuNotPolicy>,
    val promptProfile: TuNotPromptProfile,
    val internetValidated: Boolean
) {
    val connectivityLabel: String
        get() = if (internetValidated) "online" else "offline"
}

class TuNotHarness(context: Context) {
    private val appContext = context.applicationContext

    fun internetValidated(): Boolean {
        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun shouldWarmGemma(
        preference: String,
        mistralConfigured: Boolean,
        gemmaAvailable: Boolean
    ): Boolean {
        if (!gemmaAvailable) return false
        return when (preference) {
            "Gemma 4 local" -> true
            "Automático" -> !mistralConfigured || !internetValidated()
            else -> false
        }
    }

    fun plan(
        preference: String,
        mistralConfigured: Boolean,
        gemmaAvailable: Boolean,
        hasNotes: Boolean,
        hasTranscript: Boolean,
        hasVocabulary: Boolean,
        strictSources: Boolean,
        question: String,
        webRequested: Boolean,
        artifactRequest: Boolean
    ): TuNotExecutionPlan {
        val internet = internetValidated()
        val hasLocalMaterial = hasNotes || hasTranscript || hasVocabulary

        val enabledTools = linkedSetOf<TuNotTool>()
        if (hasNotes) enabledTools += TuNotTool.NOTES
        if (hasTranscript) enabledTools += TuNotTool.TRANSCRIPT
        if (hasVocabulary) enabledTools += TuNotTool.VOCABULARY
        if (internet && !strictSources && webRequested) enabledTools += TuNotTool.WEB_RESEARCH

        val primary = when (preference) {
            "Gemma 4 local" -> if (gemmaAvailable) TuNotEngine.GEMMA else TuNotEngine.LOCAL_BASIC
            "Local básico" -> {
                if (!strictSources && !hasLocalMaterial && gemmaAvailable) TuNotEngine.GEMMA
                else TuNotEngine.LOCAL_BASIC
            }
            "Mistral" -> {
                if (mistralConfigured && internet) TuNotEngine.MISTRAL
                else if (gemmaAvailable) TuNotEngine.GEMMA
                else TuNotEngine.LOCAL_BASIC
            }
            else -> {
                if (mistralConfigured && internet) TuNotEngine.MISTRAL
                else if (gemmaAvailable) TuNotEngine.GEMMA
                else TuNotEngine.LOCAL_BASIC
            }
        }

        val fallbackChain = when (primary) {
            TuNotEngine.MISTRAL -> buildList {
                if (gemmaAvailable) add(TuNotEngine.GEMMA)
                add(TuNotEngine.LOCAL_BASIC)
            }
            TuNotEngine.GEMMA -> listOf(TuNotEngine.LOCAL_BASIC)
            TuNotEngine.LOCAL_BASIC -> emptyList()
        }

        val policies = linkedSetOf(TuNotPolicy.CONNECTIVITY, TuNotPolicy.LOCAL_PRIVACY)
        if (strictSources) policies += TuNotPolicy.SOURCE_ONLY
        if (isCatholicAcademicQuestion(question)) policies += TuNotPolicy.CATHOLIC_ACADEMIC

        val promptProfile = when {
            strictSources -> TuNotPromptProfile.SOURCE_ONLY
            artifactRequest -> TuNotPromptProfile.ARTIFACT
            isStableDefinition(question) -> TuNotPromptProfile.COMPACT
            else -> TuNotPromptProfile.STANDARD
        }

        return TuNotExecutionPlan(
            primaryEngine = primary,
            fallbackChain = fallbackChain,
            enabledTools = enabledTools,
            policies = policies,
            promptProfile = promptProfile,
            internetValidated = internet
        )
    }

    private fun isStableDefinition(question: String): Boolean {
        val q = normalize(question)
        if (q.length !in 3..160) return false
        val startsLikeDefinition = listOf(
            "que es ", "que significa ", "define ", "definicion de ", "quien es ", "quien fue "
        ).any(q::startsWith)
        val asksFreshness = listOf(
            "hoy", "actual", "actualmente", "ultimo", "reciente", "noticia", "verifica", "fuente oficial"
        ).any(q::contains)
        return startsLikeDefinition && !asksFreshness
    }

    private fun isCatholicAcademicQuestion(question: String): Boolean {
        val q = normalize(question)
        return listOf(
            "teolog", "trinidad", "cristolog", "hipostasis", "ousia", "biblia", "escritura",
            "patrolog", "liturg", "magister", "catecismo", "canon", "sacramento", "iglesia"
        ).any(q::contains)
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
}
''', encoding="utf-8")


# 2) Route current TuNot service through the harness without replacing proven providers.
ai_path = root / "app/src/main/java/com/notcan/app/ai/NotCanAiService.kt"
replace_once(
    ai_path,
    "import android.net.ConnectivityManager\nimport android.net.NetworkCapabilities\n",
    "import com.notcan.app.ai.harness.TuNotEngine\nimport com.notcan.app.ai.harness.TuNotHarness\nimport com.notcan.app.ai.harness.TuNotTool\n"
)
replace_once(
    ai_path,
    "    private val webResearch = WebResearchService(appContext)\n    private val localGemma = LiteRtGemmaTuNotEngine(appContext)\n",
    "    private val webResearch = WebResearchService(appContext)\n    private val localGemma = LiteRtGemmaTuNotEngine(appContext)\n    private val harness = TuNotHarness(appContext)\n"
)
replace_once(
    ai_path,
    '''    suspend fun warmLocalGemmaIfSelected(): String? {\n        val preference = preferences.aiEnginePreference\n        val shouldWarm = preference == "Gemma 4 local" ||\n            (preference == "Automático" && (!isConfigured() || !hasValidatedInternet()))\n        if (!shouldWarm || !localGemma.isAvailable()) return null\n        return localGemma.warmUp()\n    }\n''',
    '''    suspend fun warmLocalGemmaIfSelected(): String? {\n        val available = localGemma.isAvailable()\n        val shouldWarm = harness.shouldWarmGemma(\n            preference = preferences.aiEnginePreference,\n            mistralConfigured = isConfigured(),\n            gemmaAvailable = available\n        )\n        if (!shouldWarm || !available) return null\n        return localGemma.warmUp()\n    }\n'''
)
replace_once(
    ai_path,
    '''        val plainNotes = sourcePlainText(notes)\n        val plainTranscript = sourcePlainText(transcript)\n        val internetAvailable = hasValidatedInternet()\n\n        val wantsWeb = internetAvailable && !strictSources && (forcedWeb || (autoWeb && WebResearchService.shouldAutoSearch(cleanQuestion)))\n        val webResults = if (wantsWeb) {\n            runCatching { webResearch.research(cleanQuestion, limit = 5, readTop = 3) }.getOrDefault(emptyList())\n        } else emptyList()\n        val webContext = webResearch.formatForPrompt(webResults)\n        val vocabularyContext = runCatching { loadVocabularyContext(subjectName, vocabularyRequested) }.getOrDefault("")\n        val hasLocalStudyMaterial = plainNotes.isNotBlank() || plainTranscript.isNotBlank() || vocabularyContext.isNotBlank()\n''',
    '''        val plainNotes = sourcePlainText(notes)\n        val plainTranscript = sourcePlainText(transcript)\n        val vocabularyContext = runCatching { loadVocabularyContext(subjectName, vocabularyRequested) }.getOrDefault("")\n        val webRequested = !strictSources && (forcedWeb || (autoWeb && WebResearchService.shouldAutoSearch(cleanQuestion)))\n        val executionPlan = harness.plan(\n            preference = preferences.aiEnginePreference,\n            mistralConfigured = isConfigured(),\n            gemmaAvailable = localGemma.isAvailable(),\n            hasNotes = plainNotes.isNotBlank(),\n            hasTranscript = plainTranscript.isNotBlank(),\n            hasVocabulary = vocabularyContext.isNotBlank(),\n            strictSources = strictSources,\n            question = cleanQuestion,\n            webRequested = webRequested,\n            artifactRequest = mapRequest || flashcardRequest || quizRequest\n        )\n        val wantsWeb = TuNotTool.WEB_RESEARCH in executionPlan.enabledTools\n        val webResults = if (wantsWeb) {\n            runCatching { webResearch.research(cleanQuestion, limit = 5, readTop = 3) }.getOrDefault(emptyList())\n        } else emptyList()\n        val webContext = webResearch.formatForPrompt(webResults)\n'''
)
replace_once(
    ai_path,
    '            val connectivityLabel = if (internetAvailable) "online" else "offline"\n',
    '            val connectivityLabel = executionPlan.connectivityLabel\n'
)
replace_once(
    ai_path,
    '''        when (preferences.aiEnginePreference) {\n            "Gemma 4 local" -> return localFallback(allowGemma = true)\n            "Local básico" -> {\n                // El motor extractivo necesita fuentes. Sin fuentes y fuera de Solo mis fuentes,\n                // Gemma es el respaldo local útil aunque no haya Internet.\n                val shouldEscalateToGemma = !strictSources && !hasLocalStudyMaterial && localGemma.isAvailable()\n                return localFallback(allowGemma = shouldEscalateToGemma)\n            }\n        }\n\n        // En Automático, no intentes una llamada Mistral cuando Android no tiene Internet validado.\n        // Si hay Internet y Mistral está configurado, Mistral sigue siendo el motor online preferido.\n        if (!isConfigured() || !internetAvailable) return localFallback()\n''',
    '''        when (executionPlan.primaryEngine) {\n            TuNotEngine.GEMMA -> return localFallback(allowGemma = true)\n            TuNotEngine.LOCAL_BASIC -> return localFallback(allowGemma = false)\n            TuNotEngine.MISTRAL -> Unit\n        }\n'''
)
replace_once(
    ai_path,
    '''        return try {\n            markEngine("Mistral · online", sendToMistral(prompt))\n        } catch (_: Throwable) {\n            localFallback()\n        }\n    }\n\n    private fun hasValidatedInternet(): Boolean {\n        val manager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager\n            ?: return false\n        val network = manager.activeNetwork ?: return false\n        val capabilities = manager.getNetworkCapabilities(network) ?: return false\n        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&\n            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)\n    }\n''',
    '''        return try {\n            markEngine("Mistral · online", sendToMistral(prompt))\n        } catch (_: Throwable) {\n            localFallback(allowGemma = TuNotEngine.GEMMA in executionPlan.fallbackChain)\n        }\n    }\n'''
)


# 3) Phone UI: always expose Settings without crowding the primary bottom destinations.
root_ui = root / "app/src/main/java/com/notcan/app/ui/home/NotCanRootV5.kt"
replace_once(
    root_ui,
    '''                            subjectContextActive = subjectContextActive,\n                            onOpenClasses = { page = 1; onOpenClasses() },\n                            darkTheme = darkTheme,\n''',
    '''                            subjectContextActive = subjectContextActive,\n                            onOpenClasses = { page = 1; onOpenClasses() },\n                            showSettingsShortcut = false,\n                            onOpenSettings = { page = 6; navExpanded = false },\n                            darkTheme = darkTheme,\n'''
)
replace_once(
    root_ui,
    '''                        subjectContextActive = subjectContextActive,\n                        onOpenClasses = { page = 1; onOpenClasses() },\n                        darkTheme = darkTheme,\n''',
    '''                        subjectContextActive = subjectContextActive,\n                        onOpenClasses = { page = 1; onOpenClasses() },\n                        showSettingsShortcut = true,\n                        onOpenSettings = { page = 6 },\n                        darkTheme = darkTheme,\n'''
)
replace_once(
    root_ui,
    '''    subjectContextActive: Boolean,\n    onOpenClasses: () -> Unit,\n    darkTheme: Boolean,\n''',
    '''    subjectContextActive: Boolean,\n    onOpenClasses: () -> Unit,\n    showSettingsShortcut: Boolean,\n    onOpenSettings: () -> Unit,\n    darkTheme: Boolean,\n'''
)
replace_once(
    root_ui,
    '''                Text(title, color = NotCanOffWhite, style = MaterialTheme.typography.titleLarge, maxLines = 1)\n''',
    '''                Text(\n                    title,\n                    color = NotCanOffWhite,\n                    style = if (showSettingsShortcut) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,\n                    maxLines = 1\n                )\n'''
)
replace_once(
    root_ui,
    '''            IconButton(onClick = onToggleDoNotDisturb) {\n                Icon(Icons.Default.NotificationsOff, "No molestar", tint = NotCanGray)\n            }\n''',
    '''            if (showSettingsShortcut && page != 6) {\n                IconButton(onClick = onOpenSettings) {\n                    Icon(NotCanIcons.Settings, "Configuración", tint = NotCanBlue)\n                }\n            }\n            IconButton(onClick = onToggleDoNotDisturb) {\n                Icon(Icons.Default.NotificationsOff, "No molestar", tint = NotCanGray)\n            }\n'''
)
replace_once(
    root_ui,
    '''                Surface(\n                    color = if (recordingActive) NotCanBlue.copy(alpha = 0.18f) else NotCanSurface,\n''',
    '''                IconButton(onClick = { onNavigate(6) }) {\n                    Icon(NotCanIcons.Settings, "Configuración", tint = NotCanBlue)\n                }\n                Surface(\n                    color = if (recordingActive) NotCanBlue.copy(alpha = 0.18f) else NotCanSurface,\n'''
)
replace_once(
    root_ui,
    '''                TextButton(onClick = { onNavigate(4) }) { Text("Calificaciones") }\n                TextButton(onClick = { onNavigate(5) }) { Text("TuNot") }\n''',
    '''                TextButton(onClick = { onNavigate(4) }) { Text("Calificaciones") }\n                TextButton(onClick = { onNavigate(5) }) { Text("TuNot") }\n                TextButton(onClick = { onNavigate(6) }) { Text("Ajustes") }\n'''
)


# 4) Version + architecture notes.
gradle = root / "app/build.gradle.kts"
replace_once(gradle, '        versionCode = 59\n        versionName = "0.8.35"\n', '        versionCode = 60\n        versionName = "0.8.36"\n')

docs = root / "docs/ARCHITECTURE.md"
arch = docs.read_text(encoding="utf-8")
section = r'''

## TuNot Harness nativo

Desde 0.8.36, la orquestación de TuNot se separa progresivamente del servicio monolítico mediante `ai/harness/TuNotHarness.kt`.

- **ModelRegistry**: Mistral, Gemma 4 local y Local básico se describen como motores intercambiables.
- **ToolRegistry**: apuntes, transcripciones, vocabulario y búsqueda web son herramientas registradas; calendario, calificaciones y documentos quedan preparados para conectarse sin reescribir el router.
- **Policies**: conectividad, Solo mis fuentes, precisión académica católica y privacidad local forman parte explícita del plan de ejecución.
- **ExecutionPlan**: el harness decide motor primario, fallback, herramientas permitidas y perfil de prompt según conectividad, fuentes y tipo de solicitud.

La implementación es nativa Kotlin/Android y no incorpora runtimes Node/Electron al APK. El objetivo es conservar la inferencia LiteRT/GPU y permitir crecimiento modular.
'''
if "## TuNot Harness nativo" not in arch:
    docs.write_text(arch.rstrip() + section + "\n", encoding="utf-8")

print("v0.8.36 harness + phone UI patch applied")
