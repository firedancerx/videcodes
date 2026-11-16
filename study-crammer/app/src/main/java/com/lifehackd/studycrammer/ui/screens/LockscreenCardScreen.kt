package com.lifehackd.studycrammer.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifehackd.studycrammer.data.TriageCard
import com.lifehackd.studycrammer.data.Urgency
import com.lifehackd.studycrammer.ui.components.CrammerTopBar
import com.lifehackd.studycrammer.ui.components.ErrorCard
import com.lifehackd.studycrammer.ui.components.GroundedBadge
import com.lifehackd.studycrammer.ui.components.PrimaryButton
import com.lifehackd.studycrammer.ui.components.SecondaryButton
import com.lifehackd.studycrammer.ui.theme.Charcoal
import com.lifehackd.studycrammer.ui.theme.CodeRed
import com.lifehackd.studycrammer.ui.theme.Lime
import com.lifehackd.studycrammer.ui.theme.Mono
import com.lifehackd.studycrammer.ui.theme.TextHi
import com.lifehackd.studycrammer.ui.theme.TextMid
import com.lifehackd.studycrammer.ui.viewmodel.TriageViewModel
import com.lifehackd.studycrammer.util.ImageExport
import com.lifehackd.studycrammer.util.Links
import kotlinx.coroutines.launch

/** Stage 4 — Code-Red triage rendered to a phone-shaped card, captured via GraphicsLayer → PNG. */
@Composable
fun LockscreenCardScreen(vm: TriageViewModel, onBack: () -> Unit, onReplaceKey: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    var busy by remember { mutableStateOf(false) }

    fun export(action: suspend (Bitmap) -> Unit) {
        if (busy) return
        scope.launch {
            busy = true
            try {
                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                action(bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't render the card. Try again.", Toast.LENGTH_SHORT).show()
            } finally {
                busy = false
            }
        }
    }

    val saveToGallery: () -> Unit = {
        export { bmp ->
            val ok = ImageExport.saveToGallery(context, bmp, "cram-card-${System.currentTimeMillis()}.png")
            Toast.makeText(context, if (ok) "Saved to Pictures/StudyCrammer" else "Save failed", Toast.LENGTH_SHORT).show()
        }
    }
    val legacyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveToGallery() else Toast.makeText(context, "Storage permission needed to save", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = { CrammerTopBar("04 · Code-Red 60-Minute Triage", onBack) },
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
            when {
                ui.loading -> Column(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(color = CodeRed)
                    Text("Finding the 20% that scores 80%…", color = TextMid)
                }

                ui.error != null -> ErrorCard(
                    ui.error!!,
                    onRetry = vm::load,
                    onCheckKey = { Links.open(context, Links.AI_STUDIO_API_KEYS) },
                    onReplaceKey = onReplaceKey,
                )

                ui.card != null -> {
                    CapturableCard(layer) { TriageCardView(ui.card!!, ui.urgency) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrimaryButton(
                            "Share",
                            onClick = { export { ImageExport.share(context, it, "cram-card.png") } },
                            loading = busy,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            "Save",
                            onClick = {
                                val needsLegacyPermission = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                                    ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                                    PackageManager.PERMISSION_GRANTED
                                if (needsLegacyPermission) legacyPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE) else saveToGallery()
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SecondaryButton(
                        "🔒 Set as lock screen",
                        onClick = {
                            export { bmp ->
                                val ok = ImageExport.setAsLockscreen(context, bmp)
                                Toast.makeText(context, if (ok) "Lock screen updated. Glance, absorb, ace." else "Your launcher blocked it — use Save instead", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SecondaryButton("Regenerate", onClick = vm::load, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Records its content into [layer] every frame so it can be snapshotted to a bitmap. */
@Composable
private fun CapturableCard(layer: GraphicsLayer, content: @Composable () -> Unit) {
    Box(
        Modifier.drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            drawLayer(layer)
        },
    ) { content() }
}

@Composable
fun TriageCardView(card: TriageCard, urgency: Urgency) {
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(9f / 19.5f)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF0B0B0D), Color(0xFF16200A), Color(0xFF0B0B0D))),
                RoundedCornerShape(28.dp),
            )
            .border(2.dp, Lime.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Top ~quarter stays light so the lock-screen clock doesn't cover content.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.height(64.dp))
            Text(
                if (urgency == Urgency.CODE_RED) "🚨 CODE-RED CHEAT CARD" else "⚡ CRAM CARD",
                color = if (urgency == Urgency.CODE_RED) CodeRed else Lime,
                fontFamily = Mono,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Text(card.headline, color = TextHi, fontWeight = FontWeight.Black, fontSize = 22.sp, lineHeight = 26.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            card.items.forEachIndexed { i, item ->
                Row {
                    Text("${i + 1}", color = Lime, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.width(26.dp))
                    Column {
                        Text(item.concept, color = Lime, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.memoryHook, color = TextHi, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("⏱ ${card.lastMinuteTip}", color = TextMid, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("Grounded in your notes · 5-Minute Study Crammer", color = TextMid.copy(alpha = 0.6f), fontFamily = Mono, fontSize = 9.sp)
        }
    }
}
