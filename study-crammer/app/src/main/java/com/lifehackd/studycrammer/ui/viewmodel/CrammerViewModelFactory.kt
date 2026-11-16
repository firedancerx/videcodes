package com.lifehackd.studycrammer.ui.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifehackd.studycrammer.AppContainer
import com.lifehackd.studycrammer.data.Stage

/** Builds every ViewModel from the AppContainer (manual DI, no reflection). */
fun crammerViewModelFactory(container: AppContainer, stage: Stage = Stage.TRANSLATOR): ViewModelProvider.Factory =
    viewModelFactory {
        initializer { SetupViewModel(container.vault, container.gemini) }
        initializer { PanicRouterViewModel(container.session, container.pdfExtractor) }
        initializer { StreamStageViewModel(stage, container.repository, container.session) }
        initializer { QuizViewModel(container.repository, container.session) }
        initializer { TriageViewModel(container.repository, container.session) }
    }
