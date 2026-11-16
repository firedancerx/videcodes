package com.lifehackd.studycrammer.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.lifehackd.studycrammer.AppContainer
import com.lifehackd.studycrammer.data.Stage
import com.lifehackd.studycrammer.security.IntegrityGuard
import com.lifehackd.studycrammer.security.IntegrityState
import com.lifehackd.studycrammer.ui.screens.ApiKeySetupScreen
import com.lifehackd.studycrammer.ui.screens.CheatSheetScreen
import com.lifehackd.studycrammer.ui.screens.IntegrityFailedScreen
import com.lifehackd.studycrammer.ui.screens.LockscreenCardScreen
import com.lifehackd.studycrammer.ui.screens.PanicRouterScreen
import com.lifehackd.studycrammer.ui.screens.QuizLoopScreen
import com.lifehackd.studycrammer.ui.screens.SettingsScreen
import com.lifehackd.studycrammer.ui.screens.SplashScreen
import com.lifehackd.studycrammer.ui.viewmodel.PanicRouterViewModel
import com.lifehackd.studycrammer.ui.viewmodel.QuizViewModel
import com.lifehackd.studycrammer.ui.viewmodel.SetupViewModel
import com.lifehackd.studycrammer.ui.viewmodel.StreamStageViewModel
import com.lifehackd.studycrammer.ui.viewmodel.TriageViewModel
import com.lifehackd.studycrammer.ui.viewmodel.crammerViewModelFactory

private object Routes {
    const val SETUP = "setup"
    const val REPLACE_KEY = "replace_key"
    const val ROUTER = "router"
    const val STAGE = "stage/{stage}"
    const val QUIZ = "quiz"
    const val TRIAGE = "triage"
    const val SETTINGS = "settings"
    fun stage(s: Stage) = "stage/${s.name}"
}

/** Gate: integrity first, then "has a key?", then the app. */
@Composable
fun CrammerRoot(container: AppContainer) {
    val integrity by IntegrityGuard.state.collectAsStateWithLifecycle()
    val hasKey by container.vault.hasKey.collectAsStateWithLifecycle()
    when {
        integrity is IntegrityState.Failed -> IntegrityFailedScreen()
        integrity == IntegrityState.Checking || hasKey == null -> SplashScreen()
        else -> AppNavHost(container, startWithSetup = hasKey == false)
    }
}

@Composable
private fun AppNavHost(container: AppContainer, startWithSetup: Boolean) {
    val nav = rememberNavController()
    val start = remember { if (startWithSetup) Routes.SETUP else Routes.ROUTER }
    val factory = remember(container) { crammerViewModelFactory(container) }

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() },
    ) {
        composable(Routes.SETUP) {
            ApiKeySetupScreen(
                vm = viewModel<SetupViewModel>(factory = factory),
                onSaved = { nav.navigate(Routes.ROUTER) { popUpTo(nav.graph.id) { inclusive = true } } },
                onBack = null,
            )
        }
        composable(Routes.REPLACE_KEY) {
            ApiKeySetupScreen(
                vm = viewModel<SetupViewModel>(factory = factory),
                onSaved = { nav.popBackStack() },
                onBack = { nav.popBackStack() },
            )
        }
        composable(Routes.ROUTER) {
            PanicRouterScreen(
                vm = viewModel<PanicRouterViewModel>(factory = factory),
                onOpenStage = { stage -> nav.navigate(routeFor(stage)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.STAGE) { entry ->
            val stage = entry.arguments?.getString("stage")
                ?.let { name -> Stage.entries.firstOrNull { it.name == name } } ?: Stage.TRANSLATOR
            CheatSheetScreen(
                vm = viewModel<StreamStageViewModel>(factory = crammerViewModelFactory(container, stage)),
                onBack = { nav.popBackStack() },
                onReplaceKey = { nav.navigate(Routes.REPLACE_KEY) },
            )
        }
        composable(Routes.QUIZ) {
            QuizLoopScreen(
                vm = viewModel<QuizViewModel>(factory = factory),
                onBack = { nav.popBackStack() },
                onReplaceKey = { nav.navigate(Routes.REPLACE_KEY) },
            )
        }
        composable(Routes.TRIAGE) {
            LockscreenCardScreen(
                vm = viewModel<TriageViewModel>(factory = factory),
                onBack = { nav.popBackStack() },
                onReplaceKey = { nav.navigate(Routes.REPLACE_KEY) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                vm = viewModel<SetupViewModel>(factory = factory),
                onReplaceKey = { nav.navigate(Routes.REPLACE_KEY) },
                onKeyCleared = { nav.clearToSetup() },
                onBack = { nav.popBackStack() },
            )
        }
    }
}

private fun routeFor(stage: Stage): String = when (stage) {
    Stage.TRANSLATOR, Stage.TRAPS -> Routes.stage(stage)
    Stage.QUIZ -> Routes.QUIZ
    Stage.TRIAGE -> Routes.TRIAGE
}

private fun NavHostController.clearToSetup() = navigate(Routes.SETUP) { popUpTo(graph.id) { inclusive = true } }
