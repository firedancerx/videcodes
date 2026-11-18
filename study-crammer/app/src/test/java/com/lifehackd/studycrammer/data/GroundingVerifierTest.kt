package com.lifehackd.studycrammer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingVerifierTest {
    private val notes = """
        The mitochondria is the powerhouse of the cell. It produces ATP through cellular respiration.
        Ribosomes synthesise proteins; they are found in the cytoplasm and on the rough ER.
    """.trimIndent()
    private val index = GroundingVerifier.Index(notes)

    @Test fun exactQuoteIgnoringCaseAndPunctuation() {
        assertEquals(GroundingStatus.VERIFIED, GroundingVerifier.verify("“the MITOCHONDRIA is the powerhouse of the cell”", index))
    }

    @Test fun ellipsisJoinedFragments() {
        assertEquals(GroundingStatus.VERIFIED, GroundingVerifier.verify("Ribosomes synthesise proteins … on the rough ER", index))
    }

    @Test fun paraphraseIsCloseNotVerified() {
        assertEquals(GroundingStatus.CLOSE, GroundingVerifier.verify("cellular respiration produces ATP in the mitochondria", index))
    }

    @Test fun hallucinationIsNotFound() {
        assertEquals(GroundingStatus.NOT_FOUND, GroundingVerifier.verify("Chloroplasts perform photosynthesis using chlorophyll", index))
        assertEquals(GroundingStatus.NOT_FOUND, GroundingVerifier.verify("the cell", GroundingVerifier.Index("cellular")))
    }

    @Test fun notCoveredIsRecognised() {
        assertEquals(GroundingStatus.NOT_COVERED, GroundingVerifier.verify("Not covered in your notes", index))
    }

    @Test fun summaryCountsBlockquotes() {
        val md = """
            ## Receipts
            > The mitochondria is the powerhouse of the cell.
            > Chloroplasts are green.
            > Not covered in your notes
        """.trimIndent()
        val s = GroundingVerifier.summarize(md, index)
        assertEquals(2, s.total)
        assertEquals(1, s.verified)
        assertEquals(1, s.notFound)
        assertTrue(!s.allVerified)
    }
}
