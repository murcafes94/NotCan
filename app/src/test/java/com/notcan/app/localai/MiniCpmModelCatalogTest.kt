package com.notcan.app.localai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniCpmModelCatalogTest {

    @Test
    fun `litert model files and ids are unique`() {
        val ids = MiniCpmLiteRtCatalog.models.map { it.id }
        val files = MiniCpmLiteRtCatalog.models.map { it.fileName }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals(files.size, files.toSet().size)
    }

    @Test
    fun `all experimental downloads use https and have conservative size validation`() {
        MiniCpmLiteRtCatalog.models.forEach { spec ->
            assertTrue(spec.downloadUrl.startsWith("https://"))
            assertTrue(spec.fileName.endsWith(".litertlm"))
            assertTrue(spec.minimumValidBytes > 0L)
            assertTrue(spec.minimumValidBytes < spec.approximateBytes)
            assertEquals(MiniCpmLiteRtCatalog.REQUIRED_LITERT_VERSION, spec.requiredLiteRtVersion)
        }
    }

    @Test
    fun `2b int4 is the shared cpu gpu phone candidate`() {
        val spec = MiniCpmLiteRtCatalog.spec(MiniCpmModelId.MINICPM5_2B_INT4)

        assertTrue(MiniCpmBackend.CPU in spec.backends)
        assertTrue(MiniCpmBackend.GPU in spec.backends)
        assertEquals(1_550_000_000L, spec.approximateBytes)
    }

    @Test
    fun `vision candidate keeps model and projector separate`() {
        val vision = MiniCpmVisionCatalog.MINI_CPM_V_4_6

        assertTrue(vision.modelFileName.endsWith(".gguf"))
        assertTrue(vision.projectorFileName.endsWith(".gguf"))
        assertTrue(vision.modelDownloadUrl.startsWith("https://"))
        assertTrue(vision.projectorDownloadUrl.startsWith("https://"))
        assertTrue(vision.approximateTotalBytes > 1_000_000_000L)
    }
}
