package com.lifehackd.studycrammer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifehackd.studycrammer.BuildConfig
import com.lifehackd.studycrammer.data.GeminiStreamingClient
import com.lifehackd.studycrammer.security.IntegrityGuard
import com.lifehackd.studycrammer.security.IntegrityState
import com.lifehackd.studycrammer.ui.components.CrammerTopBar
import com.lifehackd.studycrammer.ui.components.PrimaryButton
import com.lifehackd.studycrammer.ui.components.SecondaryButton
import com.lifehackd.studycrammer.ui.components.SectionLabel
import com.lifehackd.studycrammer.ui.components.SurfaceCard
import com.lifehackd.studycrammer.ui.theme.Charcoal
import com.lifehackd.studycrammer.ui.theme.CodeRed
import com.lifehackd.studycrammer.ui.theme.TextMid
import com.lifehackd.studycrammer.ui.viewmodel.SetupViewModel
import com.lifehackd.studycrammer.util.Links

@Composable
fun SettingsScreen(vm: SetupViewModel, onReplaceKey: () -> Unit, onKeyCleared: () -> Unit, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val integrity by IntegrityGuard.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

    LaunchedEffect(ui.cleared) { if (ui.cleared) onKeyCleared() }

    Scaffold(topBar = { CrammerTopBar("Settings", onBack) }, containerColor = Charcoal) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionLabel("Gemini API key")
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(ui.maskedExisting ?: "No key saved", style = MaterialTheme.typography.titleMedium)
                    Text("Encrypted with Android Keystore · AES-256-GCM · never backed up", color = TextMid, style = MaterialTheme.typography.bodyMedium)
                }
            }
            PrimaryButton("Replace key", onClick = onReplaceKey, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Clear key", onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth())
            SecondaryButton("Manage keys & quota in AI Studio ↗", onClick = { Links.open(context, Links.AI_STUDIO_API_KEYS) }, modifier = Modifier.fillMaxWidth())

            SectionLabel("About")
            SurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Model: ${GeminiStreamingClient.DEFAULT_MODEL}", style = MaterialTheme.typography.bodyMedium)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when (integrity) {
                            IntegrityState.Verified -> if (BuildConfig.DEBUG) "Integrity: debug build (reporting only)" else "Integrity: verified ✓"
                            IntegrityState.Checking -> "Integrity: checking…"
                            is IntegrityState.Failed -> "Integrity: failed"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMid,
                    )
                    Text("No backend. No analytics. Your notes never leave the phone except to Google Gemini.", color = TextMid, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear your API key?") },
            text = { Text("You'll need to paste a key again before your next cram session.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; vm.clearKey() }) { Text("Clear", color = CodeRed) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
            containerColor = Charcoal,
        )
    }
}
