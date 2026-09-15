package com.notcan.app.ai.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ModelProviderGatewayTest {

    @Test
    fun `http authentication failures are not retryable`() {
        val error = ProviderFailureClassifier.fromHttp(
            provider = ModelProviderId.MISTRAL_AGENT,
            statusCode = 401,
            message = "invalid api key"
        )

        assertEquals(ProviderFailureKind.AUTHENTICATION, error.kind)
        assertFalse(error.retryable)
    }

    @Test
    fun `quota flavored 429 is distinguished from ordinary rate limit`() {
        val quota = ProviderFailureClassifier.fromHttp(
            provider = ModelProviderId.MISTRAL_AGENT,
            statusCode = 429,
            message = "billing quota exhausted"
        )
        val rateLimit = ProviderFailureClassifier.fromHttp(
            provider = ModelProviderId.MISTRAL_AGENT,
            statusCode = 429,
            message = "too many requests"
        )

        assertEquals(ProviderFailureKind.QUOTA, quota.kind)
        assertEquals(ProviderFailureKind.RATE_LIMIT, rateLimit.kind)
        assertFalse(quota.retryable)
        assertFalse(rateLimit.retryable)
    }

    @Test
    fun `server failures are classified as transient`() {
        val error = ProviderFailureClassifier.fromHttp(
            provider = ModelProviderId.MISTRAL_AGENT,
            statusCode = 503,
            message = "service unavailable"
        )

        assertEquals(ProviderFailureKind.SERVER, error.kind)
        assertTrue(error.retryable)
    }

    @Test
    fun `circuit opens after repeated transient failures and later allows probe`() {
        var clock = 1_000L
        val breaker = ProviderCircuitBreaker(
            failureThreshold = 2,
            openDurationMs = 30_000L,
            nowMs = { clock }
        )
        val transient = ProviderException(
            provider = ModelProviderId.MISTRAL_AGENT,
            kind = ProviderFailureKind.NETWORK,
            message = "offline"
        )

        breaker.recordFailure(transient)
        assertFalse(breaker.isOpen())
        breaker.recordFailure(transient)
        assertTrue(breaker.isOpen())

        try {
            breaker.beforeCall(ModelProviderId.MISTRAL_AGENT)
            fail("Expected an open-circuit failure")
        } catch (error: ProviderException) {
            assertEquals(ProviderFailureKind.CIRCUIT_OPEN, error.kind)
        }

        clock += 30_001L
        breaker.beforeCall(ModelProviderId.MISTRAL_AGENT)
        assertFalse(breaker.isOpen())
        breaker.recordSuccess()
        assertFalse(breaker.isOpen())
    }

    @Test
    fun `permanent authentication failure does not poison provider health`() {
        val breaker = ProviderCircuitBreaker(failureThreshold = 1)
        breaker.recordFailure(
            ProviderException(
                provider = ModelProviderId.MISTRAL_AGENT,
                kind = ProviderFailureKind.AUTHENTICATION,
                message = "bad key"
            )
        )

        assertFalse(breaker.isOpen())
        breaker.beforeCall(ModelProviderId.MISTRAL_AGENT)
    }
}
