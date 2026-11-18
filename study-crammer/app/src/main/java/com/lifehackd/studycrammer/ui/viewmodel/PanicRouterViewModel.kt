package com.lifehackd.studycrammer.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifehackd.studycrammer.data.PdfExtractor
import com.lifehackd.studycrammer.data.StudySession
import com.lifehackd.studycrammer.data.StudySessionStore
import com.lifehackd.studycrammer.data.Urgency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Home screen: urgency + notes intake (paste or PDF/TXT import) + chunk picker. */
class PanicRouterViewModel(
    private val store: StudySessionStore,
    private val pdf: PdfExtractor,
) : ViewModel() {

    data class ImportedDoc(val name: String, val pages: Int, val chars: Int, val truncated: Boolean)

    data class UiState(
        val draft: String = "",
        val importing: Boolean = false,
        val importError: String? = null,
        val imported: ImportedDoc? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()
    val session: StateFlow<StudySession> = store.session

    private var commitJob: Job? = null

    fun onDraftChange(text: String) {
        _ui.update { it.copy(draft = text) }
        // Debounced: chunking a 50k-char paste on every keystroke would jank the UI.
        commitJob?.cancel()
        commitJob = viewModelScope.launch(Dispatchers.Default) {
            delay(350)
            if (text.isBlank()) store.clearNotes() else store.setNotes(text, null)
        }
    }

    fun setUrgency(urgency: Urgency) = store.setUrgency(urgency)

    fun selectChunk(index: Int) = store.selectChunk(index)

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(importing = true, importError = null) }
            try {
                val r = pdf.extract(uri)
                commitJob?.cancel()
                store.setNotes(r.text, r.displayName)
                _ui.update {
                    it.copy(
                        importing = false,
                        draft = "",
                        imported = ImportedDoc(r.displayName, r.pageCount, r.text.length, r.truncated),
                    )
                }
            } catch (e: PdfExtractor.ImportException) {
                _ui.update { it.copy(importing = false, importError = e.message) }
            } catch (e: Exception) {
                _ui.update { it.copy(importing = false, importError = "Couldn't read that file.") }
            }
        }
    }

    fun clearImport() {
        store.clearNotes()
        _ui.update { it.copy(imported = null, importError = null) }
    }

    /** Flushes a pending debounced commit so the stage screen sees the latest paste. */
    fun ensureCommitted(): Boolean {
        val state = _ui.value
        if (state.imported == null && state.draft.isNotBlank()) {
            commitJob?.cancel()
            store.setNotes(state.draft, null)
        }
        return store.current().hasNotes
    }
}
