package com.lifehackd.studycrammer.data

/**
 * Client-side "receipts check": proves (or disproves) that a snippet the model quoted
 * really appears in the student's notes. This is the app's second anti-hallucination
 * layer — the first is the system instruction; this one doesn't trust the model at all.
 */
enum class GroundingStatus { VERIFIED, CLOSE, NOT_FOUND, NOT_COVERED }

object GroundingVerifier {
    const val NOT_COVERED = "Not covered in your notes"

    private val NON_ALNUM = Regex("[^\\p{L}\\p{N}]+")
    private val ELLIPSIS = Regex("\\.\\.\\.|…|\\[\\s*\\.\\.\\.\\s*]")

    fun normalize(s: String): String = s.lowercase().replace(NON_ALNUM, " ").trim()

    /** Pre-normalised notes; build once per chunk, reuse for every snippet. */
    class Index(notes: String) {
        internal val normalized: String = " " + normalize(notes) + " "
        internal val words: Set<String> = normalized.split(' ').filter { it.isNotEmpty() }.toHashSet()
    }

    fun verify(snippet: String, index: Index): GroundingStatus {
        val raw = snippet.trim().trim('"', '“', '”', '\'', '‘', '’', '«', '»').trim()
        if (raw.isEmpty()) return GroundingStatus.NOT_FOUND
        if (raw.contains(NOT_COVERED, ignoreCase = true)) return GroundingStatus.NOT_COVERED
        val parts = raw.split(ELLIPSIS).map { normalize(it) }.filter { it.isNotBlank() }
        if (parts.isNotEmpty() && parts.all { index.normalized.contains(" $it ") }) {
            return GroundingStatus.VERIFIED
        }
        val words = normalize(raw).split(' ').filter { it.length > 2 }
        if (words.isEmpty()) return GroundingStatus.NOT_FOUND
        val coverage = words.count { it in index.words }.toDouble() / words.size
        return if (coverage >= 0.8) GroundingStatus.CLOSE else GroundingStatus.NOT_FOUND
    }

    data class Summary(val total: Int, val verified: Int, val close: Int, val notFound: Int) {
        val allVerified: Boolean get() = total > 0 && verified == total
    }

    /** Checks every Markdown blockquote ("> ...") line — the prompts put receipts there. */
    fun summarize(markdown: String, index: Index): Summary {
        val quotes = markdown.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith(">") }
            .map { it.removePrefix(">").trim() }
            .filter { it.isNotEmpty() }
            .toList()
        val statuses = quotes.map { verify(it, index) }.filter { it != GroundingStatus.NOT_COVERED }
        return Summary(
            total = statuses.size,
            verified = statuses.count { it == GroundingStatus.VERIFIED },
            close = statuses.count { it == GroundingStatus.CLOSE },
            notFound = statuses.count { it == GroundingStatus.NOT_FOUND },
        )
    }
}
