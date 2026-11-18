package com.lifehackd.studycrammer.data

/**
 * Cleans extracted/pasted text and splits it into <= [MAX_CHARS] chunks, preferring
 * paragraph boundaries, then sentence boundaries, then whitespace, and only then a hard cut.
 * Every returned chunk is guaranteed to be non-blank and <= maxChars long.
 */
object TextChunker {
    const val MAX_CHARS = 12_000

    private val PARAGRAPH = Regex("\\n\\s*\\n")
    private val SENTENCE = Regex("(?<=[.!?])\\s+")
    private val HYPHEN_BREAK = Regex("(\\p{L})-\\n(\\p{Ll})")
    private val INLINE_WS = Regex("[ \\t\\x0B\\f\\u00A0]+")
    private val SPACE_AROUND_NL = Regex(" *\\n *")
    private val MANY_NL = Regex("\\n{3,}")

    fun normalize(raw: String): String = raw
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(HYPHEN_BREAK, "$1$2") // "photo-\nsynthesis" -> "photosynthesis"
        .replace(INLINE_WS, " ")
        .replace(SPACE_AROUND_NL, "\n")
        .replace(MANY_NL, "\n\n")
        .trim()

    fun chunk(text: String, maxChars: Int = MAX_CHARS): List<String> {
        require(maxChars >= 100) { "maxChars too small" }
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        if (clean.length <= maxChars) return listOf(clean)
        val pieces = PARAGRAPH.split(clean)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { splitOversized(it, maxChars) }
        return pack(pieces, maxChars, "\n\n")
    }

    private fun splitOversized(paragraph: String, max: Int): List<String> {
        if (paragraph.length <= max) return listOf(paragraph)
        val sentences = SENTENCE.split(paragraph).flatMap { hardSplit(it.trim(), max) }
        return pack(sentences, max, " ")
    }

    private fun hardSplit(s: String, max: Int): List<String> {
        if (s.length <= max) return listOf(s)
        val out = mutableListOf<String>()
        var start = 0
        while (start < s.length) {
            var end = minOf(start + max, s.length)
            if (end < s.length) {
                val ws = s.lastIndexOf(' ', end)
                if (ws > start + max / 2) end = ws
            }
            val piece = s.substring(start, end).trim()
            if (piece.isNotEmpty()) out += piece
            start = end
            while (start < s.length && s[start] == ' ') start++
        }
        return out
    }

    private fun pack(pieces: List<String>, max: Int, sep: String): List<String> {
        val chunks = mutableListOf<String>()
        val sb = StringBuilder()
        for (p in pieces) {
            if (p.isEmpty()) continue
            val needed = if (sb.isEmpty()) p.length else sb.length + sep.length + p.length
            if (needed > max && sb.isNotEmpty()) {
                chunks += sb.toString()
                sb.setLength(0)
            }
            if (sb.isNotEmpty()) sb.append(sep)
            sb.append(p)
        }
        if (sb.isNotEmpty()) chunks += sb.toString()
        return chunks
    }
}
