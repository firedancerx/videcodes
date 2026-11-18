package com.lifehackd.studycrammer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifehackd.studycrammer.ui.components.CrammerTopBar
import com.lifehackd.studycrammer.ui.components.ErrorCard
import com.lifehackd.studycrammer.ui.components.GroundedBadge
import com.lifehackd.studycrammer.ui.components.MarkdownText
import com.lifehackd.studycrammer.ui.components.PrimaryButton
import com.lifehackd.studycrammer.ui.components.SecondaryButton
import com.lifehackd.studycrammer.ui.components.StreamingCursor
import com.lifehackd.studycrammer.ui.components.SurfaceCard
import com.lifehackd.studycrammer.ui.theme.Amber
import com.lifehackd.studycrammer.ui.theme.Charcoal
import com.lifehackd.studycrammer.ui.theme.Lime
import com.lifehackd.studycrammer.ui.viewmodel.StreamStageViewModel
import com.lifehackd.studycrammer.util.Links

/** Stage 1 ("WTF Did I Just Read?") and Stage 3 ("Read My Professor's Mind") — streamed. */
@Composable
fun CheatSheetScreen(vm: StreamStageViewModel, onBack: () -> Unit, onReplaceKey: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = { CrammerTopBar("0${vm.stage.number} · ${vm.stage.title}", onBack) },
        containerColor = Charcoal,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            GroundedBadge()
            if (ui.streaming && ui.text.isEmpty()) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime)
                Text("Reading your notes so you don't have to…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            MarkdownText(ui.text, verify = if (ui.streaming) null else vm::verify)
            if (ui.streaming && ui.text.isNotEmpty()) StreamingCursor()

            ui.grounding?.let { g ->
                SurfaceCard(borderColor = if (g.allVerified) Lime else Amber) {
                    Text(
                        if (g.total == 0) "Receipts check: no quotes to verify in this answer."
                        else "Receipts check: ${g.verified}/${g.total} quotes found word-for-word in your notes" +
                            (if (g.close > 0) " · ${g.close} paraphrased" else "") +
                            (if (g.notFound > 0) " · ${g.notFound} not found — double-check those" else " ✓"),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            ui.error?.let {
                ErrorCard(
                    error = it,
                    onRetry = vm::start,
                    onCheckKey = { Links.open(context, Links.AI_STUDIO_API_KEYS) },
                    onReplaceKey = onReplaceKey,
                )
            }

            if (!ui.streaming && ui.text.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Copy", onClick = { clipboard.setText(AnnotatedString(ui.text)) }, modifier = Modifier.weight(1f))
                    PrimaryButton("Regenerate", onClick = vm::start, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
