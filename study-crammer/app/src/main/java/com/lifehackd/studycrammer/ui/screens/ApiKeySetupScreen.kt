package com.lifehackd.studycrammer.ui.screens

import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifehackd.studycrammer.ui.components.CrammerTopBar
import com.lifehackd.studycrammer.ui.components.ErrorCard
import com.lifehackd.studycrammer.ui.components.PrimaryButton
import com.lifehackd.studycrammer.ui.components.SecondaryButton
import com.lifehackd.studycrammer.ui.components.SectionLabel
import com.lifehackd.studycrammer.ui.components.SurfaceCard
import com.lifehackd.studycrammer.ui.theme.Charcoal
import com.lifehackd.studycrammer.ui.theme.Lime
import com.lifehackd.studycrammer.ui.theme.TextMid
import com.lifehackd.studycrammer.ui.viewmodel.SetupViewModel
import com.lifehackd.studycrammer.util.Links
import com.lifehackd.studycrammer.util.SecureWindowEffect

/** "30-Second Free Setup": bring your own Gemini key, verified live, encrypted on-device. */
@Composable
fun ApiKeySetupScreen(vm: SetupViewModel, onSaved: () -> Unit, onBack: (() -> Unit)?) {
    SecureWindowEffect()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    LaunchedEffect(ui.savedKey) {
        val saved = ui.savedKey ?: return@LaunchedEffect
        // Don't leave the secret sitting on the clipboard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val cm = context.getSystemService(ClipboardManager::class.java)
            val clip = cm?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (clip == saved) runCatching { cm?.clearPrimaryClip() }
        }
        vm.consumeSaved()
        onSaved()
    }

    Scaffold(
        topBar = { CrammerTopBar(if (onBack == null) "" else "Replace API key", onBack) },
        containerColor = Charcoal,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel("⚡ 30-Second Free Setup", color = Lime)
            Text("Plug in your own\nGemini key.", style = MaterialTheme.typography.displaySmall)
            Text(
                "This app uses your own free Google Gemini API key—so you get unlimited cram sessions with zero subscriptions.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMid,
            )

            Step(1, "Open Google AI Studio", "Sign in with any Google account.")
            PrimaryButton("Get my free key  ↗", onClick = { Links.open(context, Links.AI_STUDIO_API_KEYS) }, modifier = Modifier.fillMaxWidth())
            Step(2, "Tap “Create API key” → Copy", "Takes two taps. Free tier, no card needed.")
            Step(3, "Paste it here & we’ll test it", "One tiny request to Google proves it works before we save it.")

            OutlinedTextField(
                value = ui.input,
                onValueChange = vm::onInput,
                label = { Text("Gemini API key") },
                placeholder = { Text("AIza…") },
                singleLine = true,
                visualTransformation = if (ui.reveal) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { vm.testAndSave() }),
                trailingIcon = {
                    TextButton(onClick = vm::toggleReveal) { Text(if (ui.reveal) "HIDE" else "SHOW") }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(
                    "Paste from Clipboard",
                    onClick = { clipboard.getText()?.text?.let { vm.onInput(it.trim()) } },
                    modifier = Modifier.weight(1f),
                )
                PrimaryButton(
                    "Test & Save Key",
                    onClick = vm::testAndSave,
                    enabled = ui.input.isNotBlank(),
                    loading = ui.testing,
                    modifier = Modifier.weight(1f),
                )
            }

            ui.error?.let { err ->
                ErrorCard(
                    error = err,
                    onRetry = vm::testAndSave,
                    onCheckKey = { Links.open(context, Links.AI_STUDIO_API_KEYS) },
                    onReplaceKey = { vm.onInput("") },
                )
            }

            SurfaceCard {
                Text(
                    "🔒 Your key is encrypted on this phone with Android Keystore (AES-256-GCM) and sent only to Google over HTTPS. " +
                        "There is no Study Crammer server — we never see it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextMid,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$n",
            style = MaterialTheme.typography.labelLarge,
            color = Charcoal,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .size(28.dp)
                .background(Lime, CircleShape)
                .padding(top = 4.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextMid)
        }
    }
}
