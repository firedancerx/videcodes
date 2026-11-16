package com.lifehackd.studycrammer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifehackd.studycrammer.data.CramRepository
import com.lifehackd.studycrammer.data.GradeResult
import com.lifehackd.studycrammer.data.GroundingStatus
import com.lifehackd.studycrammer.data.GroundingVerifier
import com.lifehackd.studycrammer.data.QuizQuestion
import com.lifehackd.studycrammer.data.StudySessionStore
import com.lifehackd.studycrammer.data.Verdict
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Stage 2: one question at a time. The reference answer lives ONLY in this ViewModel and is
 * exposed to the UI (via [UiState.graded]) after the student has submitted a guess.
 */
class QuizViewModel(
    private val repo: CramRepository,
    private val store: StudySessionStore,
) : ViewModel() {

    sealed interface Phase {
        data object Loading : Phase
        data object Asking : Phase
        data object Grading : Phase
        data class Graded(val result: GradeResult, val answer: String, val snippet: String, val grounding: GroundingStatus) : Phase
        data object Finished : Phase
        data class Failed(val error: UiError) : Phase
    }

    data class UiState(
        val phase: Phase = Phase.Loading,
        val questionText: String = "",
        val difficulty: String = "",
        val index: Int = 0,
        val total: Int = 0,
        val guess: String = "",
        val score: Float = 0f,
        val gradeError: UiError? = null,
    )

    private var questions: List<QuizQuestion> = emptyList()
    private var groundingIndex: GroundingVerifier.Index? = null
    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        val session = store.current()
        viewModelScope.launch {
            _ui.value = UiState(phase = Phase.Loading)
            try {
                groundingIndex = withContext(Dispatchers.Default) { GroundingVerifier.Index(session.notes) }
                questions = repo.quiz(session.notes, session.urgency)
                showQuestion(0, score = 0f)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.update { it.copy(phase = Phase.Failed(UiError.from(e))) }
            }
        }
    }

    fun onGuess(text: String) = _ui.update { it.copy(guess = text.take(500), gradeError = null) }

    /** "No clue" still counts as a committed guess — the answer is never shown without one. */
    fun submit(noClue: Boolean = false) {
        val state = _ui.value
        if (state.phase != Phase.Asking) return
        val guess = if (noClue) "(I have no idea)" else state.guess.trim()
        if (guess.isEmpty()) return
        val q = questions.getOrNull(state.index) ?: return
        viewModelScope.launch {
            _ui.update { it.copy(phase = Phase.Grading, gradeError = null) }
            try {
                val result = repo.grade(q, guess)
                val delta = when (result.verdict) {
                    Verdict.CORRECT -> 1f
                    Verdict.PARTIAL -> 0.5f
                    Verdict.INCORRECT -> 0f
                }
                val grounding = groundingIndex?.let { GroundingVerifier.verify(q.sourceSnippet, it) } ?: GroundingStatus.NOT_FOUND
                _ui.update { it.copy(phase = Phase.Graded(result, q.answer, q.sourceSnippet, grounding), score = it.score + delta) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _ui.update { it.copy(phase = Phase.Asking, gradeError = UiError.from(e)) }
            }
        }
    }

    fun next() {
        val s = _ui.value
        if (s.index + 1 < questions.size) showQuestion(s.index + 1, s.score) else _ui.update { it.copy(phase = Phase.Finished) }
    }

    private fun showQuestion(i: Int, score: Float) {
        val q = questions[i]
        _ui.value = UiState(
            phase = Phase.Asking,
            questionText = q.question,
            difficulty = q.difficulty,
            index = i,
            total = questions.size,
            score = score,
        )
    }
}
