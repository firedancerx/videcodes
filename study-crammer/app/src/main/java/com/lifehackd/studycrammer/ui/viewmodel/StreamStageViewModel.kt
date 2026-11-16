package com.lifehackd.studycrammer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifehackd.studycrammer.data.CramRepository
import com.lifehackd.studycrammer.data.GroundingStatus
import com.lifehackd.studycrammer.data.GroundingVerifier
import com.lifehackd.studycrammer.data.Stage
import com.lifehackd.studycrammer.data.StudySessionStore
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Stage 1 (Translator) and Stage 3 (Trap Finder): streamed Markdown + receipts check. */
class StreamStageViewModel(
    val stage: Stage,
    private val repo: CramRepository,
    private val store: StudySessionStore,
) : ViewModel() {

    data class UiState(
        val text: String = "",
        val streaming: Boolean = false,
        val error: UiError? = null,
        val grounding: GroundingVerifier.Summary? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var job: Job? = null
    @Volatile private var index: GroundingVerifier.Index? = null

    init {
        start()
    }

    fun start() {
        job?.cancel()
        val session = store.current()
        val notes = session.notes
        job = viewModelScope.launch {
            _ui.value = UiState(streaming = true)
            val idx = withContext(Dispatchers.Default) { GroundingVerifier.Index(notes) }
            index = idx
            val flow = when (stage) {
                Stage.TRAPS -> repo.trapFinder(notes, session.urgency)
                else -> repo.translator(notes, session.urgency)
            }
            try {
                flow.collect { delta -> _ui.update { it.copy(text = it.text + delta) } }
                val summary = withContext(Dispatchers.Default) { GroundingVerifier.summarize(_ui.value.text, idx) }
                _ui.update { it.copy(streaming = false, grounding = summary) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.update { it.copy(streaming = false, error = UiError.from(e)) }
            }
        }
    }

    fun verify(snippet: String): GroundingStatus =
        index?.let { GroundingVerifier.verify(snippet, it) } ?: GroundingStatus.NOT_FOUND
}
