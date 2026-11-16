package com.lifehackd.studycrammer.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifehackd.studycrammer.data.GeminiException
import com.lifehackd.studycrammer.data.GeminiStreamingClient
import com.lifehackd.studycrammer.security.ApiKeyVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** BYOK onboarding + Settings: test a key with a live models.list ping, then encrypt & store it. */
class SetupViewModel(
    private val vault: ApiKeyVault,
    private val gemini: GeminiStreamingClient,
) : ViewModel() {

    data class UiState(
        val input: String = "",
        val reveal: Boolean = false,
        val testing: Boolean = false,
        val error: UiError? = null,
        val savedKey: String? = null, // set once; the screen clears the clipboard if it matches
        val maskedExisting: String? = null,
        val cleared: Boolean = false,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        refreshMasked()
    }

    fun onInput(value: String) = _ui.update { it.copy(input = value, error = null) }

    fun toggleReveal() = _ui.update { it.copy(reveal = !it.reveal) }

    fun testAndSave() {
        val key = ApiKeyVault.sanitize(_ui.value.input)
        if (!ApiKeyVault.looksPlausible(key)) {
            _ui.update {
                it.copy(error = UiError("That doesn't look like a Gemini API key (they usually start with “AIza”). Copy it again from AI Studio.", null))
            }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(testing = true, error = null) }
            try {
                gemini.verifyKey(key)
                withContext(Dispatchers.IO) { vault.save(key) }
                _ui.update { it.copy(testing = false, input = "", savedKey = key) }
                refreshMasked()
            } catch (e: GeminiException) {
                _ui.update { it.copy(testing = false, error = UiError.from(e)) }
            } catch (e: Exception) {
                _ui.update { it.copy(testing = false, error = UiError("Couldn't save the key securely on this device. Try again.", null)) }
            }
        }
    }

    /** Called by the screen after it has consumed [UiState.savedKey]. */
    fun consumeSaved() = _ui.update { it.copy(savedKey = null) }

    fun clearKey() {
        viewModelScope.launch(Dispatchers.IO) {
            vault.clear()
            _ui.update { it.copy(maskedExisting = null, cleared = true) }
        }
    }

    private fun refreshMasked() {
        viewModelScope.launch(Dispatchers.IO) {
            val masked = runCatching { vault.maskedKey() }.getOrNull()
            _ui.update { it.copy(maskedExisting = masked) }
        }
    }
}
