package com.lifehackd.studycrammer.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifehackd.studycrammer.data.Stage
import com.lifehackd.studycrammer.data.StudySession
import com.lifehackd.studycrammer.data.Urgency
import com.lifehackd.studycrammer.ui.components.GroundedBadge
import com.lifehackd.studycrammer.ui.components.SecondaryButton
import com.lifehackd.studycrammer.ui.components.SectionLabel
import com.lifehackd.studycrammer.ui.components.SurfaceCard
import com.lifehackd.studycrammer.ui.theme.Charcoal
import com.lifehackd.studycrammer.ui.theme.CodeRed
import com.lifehackd.studycrammer.ui.theme.Lime
import com.lifehackd.studycrammer.ui.theme.Outline
import com.lifehackd.studycrammer.ui.theme.Surface1
import com.lifehackd.studycrammer.ui.theme.TextMid
import com.lifehackd.studycrammer.ui.viewmodel.PanicRouterViewModel

/** The 10-Second Panic Router: urgency → notes → pick a stage. */
@Composable
fun PanicRouterScreen(vm: PanicRouterViewModel, onOpenStage: (Stage) -> Unit, onSettings: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let(vm::importDocument)
    }

    Scaffold(containerColor = Charcoal) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("⚡ 5-Minute Study Crammer", color = Lime, modifier = Modifier.weight(1f))
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            }
            Text("How cooked are we?", style = MaterialTheme.typography.displaySmall)

            Urgency.entries.forEach { u ->
                UrgencyCard(u, selected = session.urgency == u, onClick = { vm.setUrgency(u) })
            }

            SectionLabel("Your notes")
            if (ui.imported != null) {
                val doc = ui.imported!!
                SurfaceCard(borderColor = Lime.copy(alpha = 0.5f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📄 ${doc.name}", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                "${doc.pages} pages · ${"%,d".format(doc.chars)} chars · ${session.chunks.size} chunk(s)" +
                                    if (doc.truncated) " · first 400 pages" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMid,
                            )
                        }
                        TextButton(onClick = vm::clearImport) { Text("Clear") }
                    }
                }
            } else {
                OutlinedTextField(
                    value = ui.draft,
                    onValueChange = vm::onDraftChange,
                    placeholder = { Text("Paste messy lecture notes, slides text, anything…") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp),
                    shape = RoundedCornerShape(14.dp),
                )
                Text(
                    "${"%,d".format(ui.draft.length)} chars" +
                        if (session.chunks.size > 1) " · auto-split into ${session.chunks.size} chunks" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMid,
                )
            }

            SecondaryButton(
                if (ui.importing) "Reading PDF…" else "📎 Import PDF / TXT",
                onClick = { picker.launch(arrayOf("application/pdf", "text/plain")) },
                enabled = !ui.importing,
                modifier = Modifier.fillMaxWidth(),
            )
            AnimatedVisibility(ui.importing) { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lime) }
            ui.importError?.let { Text(it, color = CodeRed, style = MaterialTheme.typography.bodyMedium) }

            if (session.chunks.size > 1) ChunkPicker(session, vm::selectChunk)

            GroundedBadge()

            SectionLabel("Pick your weapon")
            val ready = session.hasNotes || ui.draft.trim().length >= StudySession.MIN_NOTES_CHARS
            stageOrder(session.urgency).forEach { stage ->
                StageCard(stage, enabled = ready, highlight = session.urgency == Urgency.CODE_RED && stage == Stage.TRIAGE) {
                    if (vm.ensureCommitted()) onOpenStage(stage)
                }
            }
            if (!ready) {
                Text(
                    "Add at least ${StudySession.MIN_NOTES_CHARS} characters of notes to unlock the stages.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun stageOrder(u: Urgency): List<Stage> = when (u) {
    Urgency.CODE_RED -> listOf(Stage.TRIAGE, Stage.TRAPS, Stage.QUIZ, Stage.TRANSLATOR)
    Urgency.HOURS -> listOf(Stage.TRANSLATOR, Stage.TRAPS, Stage.QUIZ, Stage.TRIAGE)
    Urgency.DAY -> listOf(Stage.TRANSLATOR, Stage.QUIZ, Stage.TRAPS, Stage.TRIAGE)
}

@Composable
private fun UrgencyCard(u: Urgency, selected: Boolean, onClick: () -> Unit) {
    val accent = if (u == Urgency.CODE_RED) CodeRed else Lime
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    val border by animateColorAsState(
        when {
            selected && u == Urgency.CODE_RED -> CodeRed.copy(alpha = pulse)
            selected -> accent
            else -> Outline
        },
        label = "urgencyBorder",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) accent.copy(alpha = 0.10f) else Surface1, RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(u.emoji, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(u.label, style = MaterialTheme.typography.titleMedium, color = if (selected) accent else MaterialTheme.colorScheme.onSurface)
            Text(u.sublabel, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
    }
}

@Composable
private fun ChunkPicker(session: StudySession, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SectionLabel("Study chunk (12k chars each — smaller = faster & fewer rate limits)")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(session.chunks) { i, chunk ->
                FilterChip(
                    selected = session.activeChunk == i,
                    onClick = { onSelect(i) },
                    label = { Text("Part ${i + 1} · ${chunk.length / 1000}k") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Lime,
                        selectedLabelColor = Charcoal,
                    ),
                )
            }
        }
    }
}

@Composable
private fun StageCard(stage: Stage, enabled: Boolean, highlight: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.4f)
            .background(if (highlight) CodeRed.copy(alpha = 0.12f) else Surface1, RoundedCornerShape(16.dp))
            .border(1.dp, if (highlight) CodeRed else Outline, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "0${stage.number}",
            style = MaterialTheme.typography.headlineMedium,
            color = if (highlight) CodeRed else Lime,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stage.title, style = MaterialTheme.typography.titleMedium)
            Text(stage.tagline, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
        Text("→", style = MaterialTheme.typography.titleLarge, color = Lime)
    }
}
