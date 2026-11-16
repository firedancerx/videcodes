package com.lifehackd.studycrammer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lifehackd.studycrammer.ui.theme.Charcoal
import com.lifehackd.studycrammer.ui.theme.Lime
import com.lifehackd.studycrammer.ui.theme.TextMid
import androidx.compose.foundation.background

/** Shown while the first native integrity scan and vault load run (~100 ms). */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().background(Charcoal), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("⚡", style = MaterialTheme.typography.displaySmall)
            CircularProgressIndicator(color = Lime, modifier = Modifier.size(28.dp))
        }
    }
}

/** Deliberately neutral: no hints about which check failed. */
@Composable
fun IntegrityFailedScreen() {
    Box(Modifier.fillMaxSize().background(Charcoal).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = TextMid, modifier = Modifier.size(48.dp))
            Text(
                "Integrity Verification Failed (Error 409). Please install the official release.",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
