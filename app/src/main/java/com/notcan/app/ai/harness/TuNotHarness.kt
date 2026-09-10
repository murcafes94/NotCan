package com.notcan.app.ai.harness

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
