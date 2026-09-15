package com.notcan.app.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRetrievalTest {

    @Test
    fun `page markers are preserved in chunks`() {
        val indexed = """
            [[NOTCAN_PAGE:1]]
            Introducción general al documento.
            [[NOTCAN_PAGE:2]]
            La hipóstasis se distingue de la ousía en el uso trinitario maduro.
        """.trimIndent()

        val chunks = SourceRetrieval.split(indexed, chunkChars = 320, overlapChars = 40)

        assertTrue(chunks.any { it.pageNumber == 1 && "Introducción" in it.text })
        assertTrue(chunks.any { it.pageNumber == 2 && "hipóstasis" in it.text })
        assertEquals(2, SourceRetrieval.pageCount(indexed))
    }

    @Test
    fun `relevant page ranks before unrelated page`() {
        val indexed = """
            [[NOTCAN_PAGE:1]]
            La fotosíntesis transforma energía luminosa en energía química en las plantas.
            [[NOTCAN_PAGE:2]]
            La mitosis distribuye los cromosomas duplicados entre dos células hijas.
            [[NOTCAN_PAGE:3]]
            La respiración celular produce ATP mediante varias etapas metabólicas.
        """.trimIndent()

        val results = SourceRetrieval.retrieve(
            indexedText = indexed,
            query = "¿Cómo funciona la mitosis y qué pasa con los cromosomas?",
            maxHits = 3,
            chunkChars = 360
        )

        assertTrue(results.isNotEmpty())
        assertEquals(2, results.first().chunk.pageNumber)
    }

    @Test
    fun `matching ignores accents and case`() {
        val indexed = """
            [[NOTCAN_PAGE:7]]
            En teología trinitaria, hipóstasis designa la Persona realmente distinta.
        """.trimIndent()

        val results = SourceRetrieval.retrieve(indexed, "HIPOSTASIS", maxHits = 2)

        assertTrue(results.isNotEmpty())
        assertEquals(7, results.first().chunk.pageNumber)
    }

    @Test
    fun `broad sample spans early and late document`() {
        val indexed = buildString {
            for (page in 1..8) {
                appendLine("[[NOTCAN_PAGE:$page]]")
                appendLine("Contenido académico de la página $page. ".repeat(20))
            }
        }

        val sample = SourceRetrieval.sample(indexed, maxChunks = 3, chunkChars = 500)
        val pages = sample.mapNotNull { it.pageNumber }

        assertTrue(pages.isNotEmpty())
        assertTrue(pages.minOrNull()!! <= 2)
        assertTrue(pages.maxOrNull()!! >= 7)
    }

    @Test
    fun `unrelated query returns no ranked chunks`() {
        val indexed = """
            [[NOTCAN_PAGE:1]]
            Geometría euclidiana y propiedades de los triángulos.
        """.trimIndent()

        val results = SourceRetrieval.retrieve(indexed, "fermentación láctica", maxHits = 4)

        assertTrue(results.isEmpty())
    }
}
