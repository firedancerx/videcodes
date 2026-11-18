package com.lifehackd.studycrammer.ui.viewmodel

import com.lifehackd.studycrammer.data.GeminiException

data class UiError(val message: String, val kind: GeminiException.Kind?) {
    val isKeyProblem: Boolean get() = kind == GeminiException.Kind.INVALID_KEY || kind == GeminiException.Kind.NO_KEY
    val isRetryable: Boolean get() = kind != GeminiException.Kind.INTEGRITY

    companion object {
        fun from(t: Throwable): UiError = when (t) {
            is GeminiException -> UiError(t.friendlyMessage, t.kind)
            else -> UiError("Something went sideways. Hit Retry.", null)
        }
    }
}
