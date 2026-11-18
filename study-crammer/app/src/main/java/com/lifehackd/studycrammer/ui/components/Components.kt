package com.lifehackd.studycrammer.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lifehackd.studycrammer.data.GroundingStatus
import com.lifehackd.studycrammer.ui.theme.Amber
import com.lifehackd.studycrammer.ui.theme.Charcoal
import com.lifehackd.studycrammer.ui.theme.CodeRed
import com.lifehackd.studycrammer.ui.theme.Lime
import com.lifehackd.studycrammer.ui.theme.Outline
import com.lifehackd.studycrammer.ui.theme.Surface1
import com.lifehackd.studycrammer.ui.theme.TextMid
import com.lifehackd.studycrammer.ui.viewmodel.UiError

/** The trust badge shown on every AI-powered screen. */
@Composable
fun GroundedBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Lime.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
            .border(1.dp, Lime.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("✓", color = Lime, fontWeight = FontWeight.Black)
        Spacer(Modifier.width(8.dp))
        Text(
            "100% Grounded in Your Notes — Zero Essay Generation / Turnitin Safe",
            style = MaterialTheme.typography.labelSmall,
            color = Lime,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrammerTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Charcoal),
    )
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Charcoal),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp), color = Charcoal, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Outline),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = TextMid) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** Friendly error with Retry, plus "check key" shortcuts for key problems. */
@Composable
fun ErrorCard(
    error: UiError,
    onRetry: (() -> Unit)?,
    onCheckKey: () -> Unit,
    onReplaceKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CodeRed.copy(alpha = 0.10f), RoundedCornerShape(14.dp))
            .border(1.dp, CodeRed.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = CodeRed)
            Spacer(Modifier.width(10.dp))
            Text(error.message, style = MaterialTheme.typography.bodyMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (error.isKeyProblem) {
                Button(onClick = onCheckKey, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Charcoal)) {
                    Text("Check key in AI Studio")
                }
                OutlinedButton(onClick = onReplaceKey) { Text("Replace key") }
            } else if (onRetry != null && error.isRetryable) {
                Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Charcoal)) {
                    Text("Retry")
                }
            }
        }
    }
}

@Composable
fun GroundingChip(status: GroundingStatus, modifier: Modifier = Modifier) {
    val (label, color) = when (status) {
        GroundingStatus.VERIFIED -> "✓ found in your notes" to Lime
        GroundingStatus.CLOSE -> "≈ paraphrased from notes" to Amber
        GroundingStatus.NOT_FOUND -> "⚠ not found verbatim — double-check" to CodeRed
        GroundingStatus.NOT_COVERED -> "∅ not covered in your notes" to TextMid
    }
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Blinking block cursor shown while tokens stream in. */
@Composable
fun StreamingCursor() {
    val alpha by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "cursorAlpha",
    )
    Box(
        Modifier
            .padding(top = 4.dp)
            .size(width = 10.dp, height = 18.dp)
            .alpha(alpha)
            .background(Lime),
    )
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, borderColor: Color = Outline, content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Surface1, RoundedCornerShape(16.dp))
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) { content() }
}
