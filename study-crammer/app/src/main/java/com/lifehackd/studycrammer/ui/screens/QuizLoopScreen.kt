package com.lifehackd.studycrammer.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifehackd.studycrammer.data.Verdict
import com.lifehackd.studycrammer.ui.components.CrammerTopBar
import com.lifehackd.studycrammer.ui.components.ErrorCard
import com.lifehackd.studycrammer.ui.components.GroundedBadge
import com.lifehackd.studycrammer.ui.components.GroundingChip
import com.lifehackd.studycrammer.ui.components.PrimaryButton
import com.lifehackd.studycrammer.ui.components.SecondaryButton
import com.lifehackd.studycrammer.ui.components.SectionLabel
import com.lifehackd.studycrammer.ui.components.SurfaceCard
import com.lifehackd.studycrammer.ui.theme.Amber
import com.lifehackd.studycrammer.ui.theme.Charcoal
import com.lifehackd.studycrammer.ui.theme.CodeRed
import com.lifehackd.studycrammer.ui.theme.Lime
import com.lifehackd.studycrammer.ui.theme.Outline
import com.lifehackd.studycrammer.ui.theme.TextMid
import com.lifehackd.studycrammer.ui.viewmodel.QuizViewModel
import com.lifehackd.studycrammer.ui.viewmodel.QuizViewModel.Phase
import com.lifehackd.studycrammer.util.Links

/** Stage 2 — Active-Recall Quizmaster. The answer is never rendered before a guess is submitted. */
@Composable
fun QuizLoopScreen(vm: QuizViewModel, onBack: () -> Unit, onReplaceKey: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val progress by animateFloatAsState(
        if (ui.total == 0) 0f else (ui.index + if (ui.phase is Phase.Graded || ui.phase == Phase.Finished) 1 else 0).toFloat() / ui.total,
        label = "quizProgress",
    )

    Scaffold(
        topBar = { CrammerTopBar("02 · Active-Recall Quizmaster", onBack) },
        containerColor = Charcoal,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GroundedBadge()
            if (ui.total > 0) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Lime, trackColor = Outline)
                Row {
                    SectionLabel("Question ${minOf(ui.index + 1, ui.total)} / ${ui.total} · ${ui.difficulty}", modifier = Modifier.weight(1f))
                    SectionLabel("Score ${formatScore(ui.score)}", color = Lime)
                }
            }

            AnimatedContent(
                targetState = ui.phase,
                transitionSpec = { (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut() },
                contentKey = { phase -> if (phase == Phase.Grading) Phase.Asking::class else phase::class },
                label = "quizPhase",
            ) { phase ->
                when (phase) {
                    Phase.Loading -> Column(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(color = Lime)
                        Text("Writing questions ONLY from your notes…", color = TextMid)
                    }

                    Phase.Asking, Phase.Grading -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(ui.questionText, style = MaterialTheme.typography.headlineSmall)
                        OutlinedTextField(
                            value = ui.guess,
                            onValueChange = vm::onGuess,
                            placeholder = { Text("Your best guess — no peeking, the answer stays locked") },
                            enabled = phase == Phase.Asking,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                            shape = RoundedCornerShape(14.dp),
                        )
                        ui.gradeError?.let {
                            ErrorCard(it, onRetry = { vm.submit() }, onCheckKey = { Links.open(context, Links.AI_STUDIO_API_KEYS) }, onReplaceKey = onReplaceKey)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SecondaryButton("🤷 No clue", onClick = { vm.submit(noClue = true) }, enabled = phase == Phase.Asking, modifier = Modifier.weight(1f))
                            PrimaryButton(
                                "Lock it in",
                                onClick = { vm.submit() },
                                enabled = ui.guess.isNotBlank(),
                                loading = phase == Phase.Grading,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }

                    is Phase.Graded -> Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        val (title, color) = when (phase.result.verdict) {
                            Verdict.CORRECT -> "✅ Nailed it" to Lime
                            Verdict.PARTIAL -> "🟡 Half credit" to Amber
                            Verdict.INCORRECT -> "❌ Not quite" to CodeRed
                        }
                        Text(title, style = MaterialTheme.typography.headlineMedium, color = color)
                        Text(phase.result.feedback, style = MaterialTheme.typography.bodyLarge)
                        SurfaceCard(borderColor = color.copy(alpha = 0.6f)) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SectionLabel("Answer")
                                Text(phase.answer, style = MaterialTheme.typography.titleMedium)
                                if (phase.snippet.isNotBlank()) {
                                    SectionLabel("📌 From your notes")
                                    Text("“${phase.snippet}”", fontStyle = FontStyle.Italic, color = TextMid)
                                    GroundingChip(phase.grounding)
                                }
                            }
                        }
                        PrimaryButton(
                            if (ui.index + 1 < ui.total) "Next question →" else "See my score",
                            onClick = vm::next,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    Phase.Finished -> Column(
                        Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        val pct = if (ui.total == 0) 0f else ui.score / ui.total
                        Text("${formatScore(ui.score)} / ${ui.total}", style = MaterialTheme.typography.displaySmall, color = Lime)
                        Text(
                            when {
                                pct >= 0.9f -> "Certified menace. The exam should be scared of YOU."
                                pct >= 0.6f -> "Solid. One more lap and you're dangerous."
                                pct >= 0.3f -> "Getting there. Hit the Trap Finder, then run it back."
                                else -> "Rough round — that's exactly why we practise. Run it back."
                            },
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        PrimaryButton("New quiz", onClick = vm::load, modifier = Modifier.fillMaxWidth())
                        SecondaryButton("Back to stages", onClick = onBack, modifier = Modifier.fillMaxWidth())
                    }

                    is Phase.Failed -> ErrorCard(
                        phase.error,
                        onRetry = vm::load,
                        onCheckKey = { Links.open(context, Links.AI_STUDIO_API_KEYS) },
                        onReplaceKey = onReplaceKey,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatScore(s: Float): String = if (s % 1f == 0f) s.toInt().toString() else "%.1f".format(s)
