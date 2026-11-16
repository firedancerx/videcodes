package com.lifehackd.studycrammer

import android.app.Application
import com.lifehackd.studycrammer.data.CramRepository
import com.lifehackd.studycrammer.data.GeminiStreamingClient
import com.lifehackd.studycrammer.data.PdfExtractor
import com.lifehackd.studycrammer.data.StudySessionStore
import com.lifehackd.studycrammer.security.ApiKeyVault
import com.lifehackd.studycrammer.security.GeminiAuthInterceptor
import com.lifehackd.studycrammer.security.IntegrityGuard
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion

class CrammerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Tamper => wipe any decrypted key from memory immediately.
        IntegrityGuard.addTamperListener { container.vault.wipeMemory() }
        // Full native scan (dex + .so + cert + runtime) off the main thread.
        IntegrityGuard.verifyAsync(this, container.appScope)
        container.appScope.launch(Dispatchers.IO) { container.vault.load() }
    }
}

/** Manual DI: one place that wires the object graph. */
class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vault = ApiKeyVault(app)
    val session = StudySessionStore()
    val pdfExtractor = PdfExtractor(app)

    private val http: OkHttpClient = OkHttpClient.Builder()
        // TLS 1.2+ only, modern cipher suites, no cleartext spec at all.
        .connectionSpecs(
            listOf(
                ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                    .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
                    .build(),
            ),
        )
        .addInterceptor(GeminiAuthInterceptor(vault))
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS) // gaps between SSE chunks
        .build()

    val gemini = GeminiStreamingClient(http)
    val repository = CramRepository(gemini)
}
