package com.notcan.app.ai.harness

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Common provider primitives for TuNot.
 *
 * The Android app must be able to fail over to a local engine without duplicating provider-specific
 * error handling in NotCanAiService. These types intentionally have no Android dependency so they can
 * be unit-tested and reused by future remote/self-hosted adapters.
 */
enum class ModelProviderId {
    MISTRAL_AGENT,
    GEMMA_LOCAL,
    MINICPM5_LOCAL,
    MINICPM_V_LOCAL,
    LOCAL_BASIC,
    OPENAI_COMPATIBLE
}

enum class ProviderCapability {
    TEXT_GENERATION,
    REASONING,
    TOOL_CALLING,
    STREAMING,
    VISION,
    VIDEO,
    LOCAL_EXECUTION
}

data class ModelProviderDescriptor(
    val id: ModelProviderId,
    val label: String,
    val local: Boolean,
    val experimental: Boolean,
    val capabilities: Set<ProviderCapability>
)

/**
 * Capability catalogue used by the Harness to evolve from provider-name routing toward capability
 * routing. MiniCPM entries are experimental until their Android runtimes pass NotCan Bench.
 */
object ModelProviderCatalog {
    val providers: List<ModelProviderDescriptor> = listOf(
        ModelProviderDescriptor(
            id = ModelProviderId.MISTRAL_AGENT,
            label = "Mistral Agent",
            local = false,
            experimental = false,
            capabilities = setOf(
                ProviderCapability.TEXT_GENERATION,
                ProviderCapability.REASONING,
                ProviderCapability.TOOL_CALLING
            )
        ),
        ModelProviderDescriptor(
            id = ModelProviderId.GEMMA_LOCAL,
            label = "Gemma 4 local",
            local = true,
            experimental = false,
            capabilities = setOf(
                ProviderCapability.TEXT_GENERATION,
                ProviderCapability.REASONING,
                ProviderCapability.STREAMING,
                ProviderCapability.LOCAL_EXECUTION
            )
        ),
        ModelProviderDescriptor(
            id = ModelProviderId.MINICPM5_LOCAL,
            label = "MiniCPM5 local",
            local = true,
            experimental = true,
            capabilities = setOf(
                ProviderCapability.TEXT_GENERATION,
                ProviderCapability.REASONING,
                ProviderCapability.TOOL_CALLING,
                ProviderCapability.STREAMING,
                ProviderCapability.LOCAL_EXECUTION
            )
        ),
        ModelProviderDescriptor(
            id = ModelProviderId.MINICPM_V_LOCAL,
            label = "MiniCPM-V local",
            local = true,
            experimental = true,
            capabilities = setOf(
                ProviderCapability.TEXT_GENERATION,
                ProviderCapability.VISION,
                ProviderCapability.VIDEO,
                ProviderCapability.STREAMING,
                ProviderCapability.LOCAL_EXECUTION
            )
        ),
        ModelProviderDescriptor(
            id = ModelProviderId.LOCAL_BASIC,
            label = "Local básico",
            local = true,
            experimental = false,
            capabilities = setOf(
                ProviderCapability.TEXT_GENERATION,
                ProviderCapability.LOCAL_EXECUTION
            )
        ),
        ModelProviderDescriptor(
            id = ModelProviderId.OPENAI_COMPATIBLE,
            label = "Endpoint OpenAI-compatible",
            local = false,
            experimental = true,
            capabilities = setOf(
                ProviderCapability.TEXT_GENERATION,
                ProviderCapability.STREAMING
            )
        )
    )

    fun descriptor(id: ModelProviderId): ModelProviderDescriptor =
        providers.first { it.id == id }

    fun providersWith(capability: ProviderCapability): List<ModelProviderDescriptor> =
        providers.filter { capability in it.capabilities }
}

enum class ProviderFailureKind {
    AUTHENTICATION,
    RATE_LIMIT,
    QUOTA,
    INVALID_REQUEST,
    NOT_FOUND,
    TIMEOUT,
    NETWORK,
    SERVER,
    CANCELLED,
    CIRCUIT_OPEN,
    UNKNOWN
}

class ProviderException(
    val provider: ModelProviderId,
    val kind: ProviderFailureKind,
    message: String,
    val statusCode: Int? = null,
    val retryable: Boolean = false,
    cause: Throwable? = null
) : IOException(message, cause)

object ProviderFailureClassifier {
    fun fromHttp(
        provider: ModelProviderId,
        statusCode: Int,
        message: String
    ): ProviderException {
        val lower = message.lowercase()
        val kind = when (statusCode) {
            401, 403 -> ProviderFailureKind.AUTHENTICATION
            404 -> ProviderFailureKind.NOT_FOUND
            408 -> ProviderFailureKind.TIMEOUT
            429 -> if ("quota" in lower || "credit" in lower || "billing" in lower) {
                ProviderFailureKind.QUOTA
            } else {
                ProviderFailureKind.RATE_LIMIT
            }
            in 400..499 -> ProviderFailureKind.INVALID_REQUEST
            in 500..599 -> ProviderFailureKind.SERVER
            else -> ProviderFailureKind.UNKNOWN
        }
        return ProviderException(
            provider = provider,
            kind = kind,
            message = message,
            statusCode = statusCode,
            retryable = kind == ProviderFailureKind.SERVER
        )
    }

    fun fromThrowable(provider: ModelProviderId, throwable: Throwable): ProviderException {
        if (throwable is ProviderException) return throwable
        val kind = when (throwable) {
            is SocketTimeoutException -> ProviderFailureKind.TIMEOUT
            is UnknownHostException, is ConnectException -> ProviderFailureKind.NETWORK
            is IOException -> ProviderFailureKind.NETWORK
            else -> ProviderFailureKind.UNKNOWN
        }
        return ProviderException(
            provider = provider,
            kind = kind,
            message = throwable.message ?: throwable.javaClass.simpleName,
            retryable = false,
            cause = throwable
        )
    }
}

/**
 * Small in-memory circuit breaker. It protects the student experience from repeated long waits when a
 * remote provider is clearly unavailable. Local providers never need to use it.
 */
class ProviderCircuitBreaker(
    private val failureThreshold: Int = 2,
    private val openDurationMs: Long = 30_000L,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    init {
        require(failureThreshold > 0)
        require(openDurationMs > 0)
    }

    private var consecutiveFailures: Int = 0
    private var openedAtMs: Long? = null

    @Synchronized
    fun beforeCall(provider: ModelProviderId) {
        val openedAt = openedAtMs ?: return
        val elapsed = nowMs() - openedAt
        if (elapsed >= openDurationMs) {
            // Half-open: allow one real call. A success will close the circuit; a failure reopens it.
            openedAtMs = null
            consecutiveFailures = failureThreshold - 1
            return
        }
        throw ProviderException(
            provider = provider,
            kind = ProviderFailureKind.CIRCUIT_OPEN,
            message = "Proveedor temporalmente no disponible; se usará el respaldo local.",
            retryable = false
        )
    }

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
        openedAtMs = null
    }

    @Synchronized
    fun recordFailure(error: ProviderException) {
        if (!countsTowardsCircuit(error.kind)) return
        consecutiveFailures += 1
        if (consecutiveFailures >= failureThreshold) {
            openedAtMs = nowMs()
        }
    }

    @Synchronized
    fun isOpen(): Boolean {
        val openedAt = openedAtMs ?: return false
        return nowMs() - openedAt < openDurationMs
    }

    private fun countsTowardsCircuit(kind: ProviderFailureKind): Boolean = when (kind) {
        ProviderFailureKind.NETWORK,
        ProviderFailureKind.TIMEOUT,
        ProviderFailureKind.SERVER -> true
        else -> false
    }
}

/** Shared per-process health state for remote providers. */
object ModelProviderHealthRegistry {
    private val breakers = ConcurrentHashMap<ModelProviderId, ProviderCircuitBreaker>()

    fun breaker(provider: ModelProviderId): ProviderCircuitBreaker =
        breakers.getOrPut(provider) { ProviderCircuitBreaker() }

    fun reset(provider: ModelProviderId) {
        breakers.remove(provider)
    }
}
