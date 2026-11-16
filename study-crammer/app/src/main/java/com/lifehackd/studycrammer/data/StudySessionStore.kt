package com.lifehackd.studycrammer.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** In-memory session shared by every screen (notes are never written to disk). */
class StudySessionStore {
    private val _session = MutableStateFlow(StudySession())
    val session: StateFlow<StudySession> = _session.asStateFlow()

    fun current(): StudySession = _session.value

    /** Normalises + chunks. Keeps the selected chunk if the text is unchanged. */
    fun setNotes(text: String, sourceName: String?) {
        val fp = 31 * text.hashCode() + text.length
        if (fp == _session.value.fingerprint && sourceName == _session.value.sourceName) return
        val chunks = TextChunker.chunk(TextChunker.normalize(text))
        _session.update {
            it.copy(
                sourceName = sourceName,
                chunks = chunks,
                activeChunk = it.activeChunk.coerceIn(0, (chunks.size - 1).coerceAtLeast(0)),
                fingerprint = fp,
            )
        }
    }

    fun selectChunk(index: Int) = _session.update {
        it.copy(activeChunk = index.coerceIn(0, (it.chunks.size - 1).coerceAtLeast(0)))
    }

    fun setUrgency(urgency: Urgency) = _session.update { it.copy(urgency = urgency) }

    fun clearNotes() = _session.update { StudySession(urgency = it.urgency) }
}
