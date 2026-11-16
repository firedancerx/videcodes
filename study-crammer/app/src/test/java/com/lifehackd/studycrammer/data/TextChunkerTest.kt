package com.lifehackd.studycrammer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextChunkerTest {

    private fun words(s: String) = s.split(Regex("\\s+")).filter { it.isNotEmpty() }

    @Test fun shortTextIsOneChunk() {
        assertEquals(listOf("hello world"), TextChunker.chunk("  hello world \n"))
        assertTrue(TextChunker.chunk("   ").isEmpty())
    }

    @Test fun longTextRespectsLimitAndLosesNothing() {
        val para = (1..60).joinToString(" ") { "Sentence number $it explains mitosis." }
        val text = (1..20).joinToString("\n\n") { "Paragraph $it. $para" }
        val chunks = TextChunker.chunk(text, 3_000)
        assertTrue(chunks.size > 1)
        chunks.forEach { assertTrue("chunk ${it.length} > 3000", it.length <= 3_000) }
        assertEquals(words(text), chunks.flatMap { words(it) })
    }

    @Test fun pathologicalNoWhitespaceIsHardSplit() {
        val blob = "x".repeat(25_000)
        val chunks = TextChunker.chunk(blob, TextChunker.MAX_CHARS)
        assertEquals(3, chunks.size)
        chunks.forEach { assertTrue(it.length <= TextChunker.MAX_CHARS) }
        assertEquals(blob, chunks.joinToString(""))
    }

    @Test fun normalizeFixesPdfArtifacts() {
        val raw = "Photo-\nsynthesis   uses\r\n\r\n\r\n\r\nlight energy."
        assertEquals("Photosynthesis uses\n\nlight energy.", TextChunker.normalize(raw))
    }
}
