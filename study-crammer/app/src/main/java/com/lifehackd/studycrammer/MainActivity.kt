package com.lifehackd.studycrammer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.lifehackd.studycrammer.security.IntegrityGuard
import com.lifehackd.studycrammer.ui.navigation.CrammerRoot
import com.lifehackd.studycrammer.ui.theme.CrammerTheme

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as CrammerApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            CrammerTheme {
                CrammerRoot(container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Runtime re-check (debugger / Frida / Xposed) every time we come to the foreground.
        IntegrityGuard.verifyAsync(this, container.appScope)
    }
}
