package com.lifehackd.studycrammer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifehackd.studycrammer.data.CramRepository
import com.lifehackd.studycrammer.data.StudySessionStore
import com.lifehackd.studycrammer.data.TriageCard
import com.lifehackd.studycrammer.data.Urgency
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Stage 4: 80/20 triage rendered as a lockscreen card. */
class TriageViewModel(
    private val repo: CramRepository,
    private val store: StudySessionStore,
) : ViewModel() {

    data class UiState(
        val loading: Boolean = true,
        val card: TriageCard? = null,
        val urgency: Urgency = Urgency.CODE_RED,
        val error: UiError? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        val session = store.current()
        viewModelScope.launch {
            _ui.value = UiState(loading = true, urgency = session.urgency)
            try {
                val card = repo.triage(session.notes, session.urgency)
                _ui.value = UiState(loading = false, card = card, urgency = session.urgency)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.value = UiState(loading = false, urgency = session.urgency, error = UiError.from(e))
            }
        }
    }
}
