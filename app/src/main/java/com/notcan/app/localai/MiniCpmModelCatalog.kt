package com.notcan.app.localai

/**
 * Models being evaluated for the MiniCPM local experiment.
 *
 * The LiteRT-LM bundles are downloaded only after an explicit user action. They are not bundled
 * inside the APK. Runtime activation is intentionally separate from download management so the
 * experiment can be rolled back without affecting Gemma 4 or the local basic fallback.
 */
enum class MiniCpmModelId {
    MINICPM5_1B_CPU,
    MINICPM5_1B_GPU,
    MINICPM5_2B_INT4
}

enum class MiniCpmBackend {
    CPU,
    GPU
}

data class MiniCpmLiteRtModelSpec(
    val id: MiniCpmModelId,
    val displayName: String,
    val fileName: String,
    val downloadUrl: String,
    val approximateBytes: Long,
    val minimumValidBytes: Long,
    val backends: Set<MiniCpmBackend>,
    val requiredLiteRtVersion: String,
    val defaultThinkingEnabled: Boolean
)

object MiniCpmLiteRtCatalog {
    const val REQUIRED_LITERT_VERSION = "0.17.0"

    val models: List<MiniCpmLiteRtModelSpec> = listOf(
        MiniCpmLiteRtModelSpec(
            id = MiniCpmModelId.MINICPM5_1B_CPU,
            displayName = "MiniCPM5-1B · CPU",
            fileName = "minicpm_wi4b32_wi8_afp32.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/MiniCPM5-1B/resolve/main/minicpm_wi4b32_wi8_afp32.litertlm?download=true",
            approximateBytes = 790_000_000L,
            minimumValidBytes = 650_000_000L,
            backends = setOf(MiniCpmBackend.CPU),
            requiredLiteRtVersion = REQUIRED_LITERT_VERSION,
            defaultThinkingEnabled = true
        ),
        MiniCpmLiteRtModelSpec(
            id = MiniCpmModelId.MINICPM5_1B_GPU,
            displayName = "MiniCPM5-1B · GPU",
            fileName = "minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/MiniCPM5-1B/resolve/main/minicpm_wi4b32_wi8_afp32_gpu_opt.litertlm?download=true",
            approximateBytes = 790_000_000L,
            minimumValidBytes = 650_000_000L,
            backends = setOf(MiniCpmBackend.GPU),
            requiredLiteRtVersion = REQUIRED_LITERT_VERSION,
            defaultThinkingEnabled = true
        ),
        MiniCpmLiteRtModelSpec(
            id = MiniCpmModelId.MINICPM5_2B_INT4,
            displayName = "MiniCPM5-2B · int4",
            fileName = "MiniCPM5-2B_int4.litertlm",
            downloadUrl = "https://huggingface.co/litert-community/MiniCPM5-2B/resolve/main/MiniCPM5-2B_int4.litertlm?download=true",
            approximateBytes = 1_550_000_000L,
            minimumValidBytes = 1_250_000_000L,
            backends = setOf(MiniCpmBackend.CPU, MiniCpmBackend.GPU),
            requiredLiteRtVersion = REQUIRED_LITERT_VERSION,
            defaultThinkingEnabled = true
        )
    )

    fun spec(id: MiniCpmModelId): MiniCpmLiteRtModelSpec =
        models.first { it.id == id }
}

/**
 * MiniCPM-V 4.6 is tracked separately because its current Android reference path is GGUF +
 * vision projector through llama.cpp rather than the LiteRT-LM text runtime used above.
 */
data class MiniCpmVisionSpec(
    val displayName: String,
    val modelFileName: String,
    val projectorFileName: String,
    val modelDownloadUrl: String,
    val projectorDownloadUrl: String,
    val approximateTotalBytes: Long,
    val recommendedRamGb: Int
)

object MiniCpmVisionCatalog {
    val MINI_CPM_V_4_6 = MiniCpmVisionSpec(
        displayName = "MiniCPM-V 4.6",
        modelFileName = "MiniCPM-V-4_6-Q4_K_M.gguf",
        projectorFileName = "mmproj-model-f16.gguf",
        modelDownloadUrl = "https://huggingface.co/openbmb/MiniCPM-V-4.6-gguf/resolve/main/MiniCPM-V-4_6-Q4_K_M.gguf?download=true",
        projectorDownloadUrl = "https://huggingface.co/openbmb/MiniCPM-V-4.6-gguf/resolve/main/mmproj-model-f16.gguf?download=true",
        approximateTotalBytes = 1_600_000_000L,
        recommendedRamGb = 6
    )
}
