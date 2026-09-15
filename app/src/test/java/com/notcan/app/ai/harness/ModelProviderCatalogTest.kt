package com.notcan.app.ai.harness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelProviderCatalogTest {

    @Test
    fun `provider ids are unique and every provider exposes text or media capability`() {
        val ids = ModelProviderCatalog.providers.map { it.id }
        assertEquals(ids.size, ids.toSet().size)

        ModelProviderCatalog.providers.forEach { provider ->
            assertTrue(
                ProviderCapability.TEXT_GENERATION in provider.capabilities ||
                    ProviderCapability.VISION in provider.capabilities ||
                    ProviderCapability.VIDEO in provider.capabilities
            )
        }
    }

    @Test
    fun `minicpm providers stay local and experimental until benchmark promotion`() {
        val text = ModelProviderCatalog.descriptor(ModelProviderId.MINICPM5_LOCAL)
        val vision = ModelProviderCatalog.descriptor(ModelProviderId.MINICPM_V_LOCAL)

        assertTrue(text.local)
        assertTrue(vision.local)
        assertTrue(text.experimental)
        assertTrue(vision.experimental)
        assertTrue(ProviderCapability.REASONING in text.capabilities)
        assertTrue(ProviderCapability.VISION in vision.capabilities)
        assertTrue(ProviderCapability.VIDEO in vision.capabilities)
    }

    @Test
    fun `stable gemma remains non experimental during minicpm evaluation`() {
        val gemma = ModelProviderCatalog.descriptor(ModelProviderId.GEMMA_LOCAL)

        assertTrue(gemma.local)
        assertFalse(gemma.experimental)
        assertTrue(ProviderCapability.STREAMING in gemma.capabilities)
    }
}
