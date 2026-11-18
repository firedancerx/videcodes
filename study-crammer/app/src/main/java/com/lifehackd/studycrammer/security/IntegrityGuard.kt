package com.lifehackd.studycrammer.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.lifehackd.studycrammer.BuildConfig
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface IntegrityState {
    data object Checking : IntegrityState
    data object Verified : IntegrityState
    /** [code] is the native failure bitmask — never shown to the user. */
    data class Failed(val code: Int) : IntegrityState
}

/**
 * Kotlin side of the native integrity engine (libintegrity_guard.so).
 *
 * - The verdict itself is computed in C++; enforcement is a compile-time C++ switch
 *   (IG_ENFORCE), so flipping BuildConfig.DEBUG in smali does not disable it.
 * - A failure is sticky for the life of the process.
 * - [isVerified] is consulted by GeminiAuthInterceptor before EVERY Gemini request.
 */
object IntegrityGuard {
    private const val TAG = "IntegrityGuard"
    private const val FAIL_NATIVE_MISSING = 1 shl 30

    private val nativeLoaded: Boolean = try {
        System.loadLibrary("integrity_guard")
        true
    } catch (t: Throwable) {
        false
    }

    private val _state = MutableStateFlow<IntegrityState>(IntegrityState.Checking)
    val state: StateFlow<IntegrityState> = _state.asStateFlow()

    private val mutex = Mutex()
    @Volatile private var fullScanDone = false
    private val tamperListeners = CopyOnWriteArrayList<() -> Unit>()

    fun addTamperListener(listener: () -> Unit) {
        tamperListeners += listener
    }

    /** True only if BOTH the Kotlin state and the native verdict say the APK is genuine. */
    fun isVerified(): Boolean =
        nativeLoaded &&
            _state.value == IntegrityState.Verified &&
            runCatching { nativeIsVerified() }.getOrDefault(false)

    /** Called from Application.onCreate (full scan) and every Activity.onResume (runtime checks). */
    fun verifyAsync(context: Context, scope: CoroutineScope) {
        val app = context.applicationContext
        scope.launch(Dispatchers.IO) { verify(app) }
    }

    suspend fun verify(context: Context): IntegrityState = mutex.withLock {
        val current = _state.value
        if (current is IntegrityState.Failed) return@withLock current

        val next: IntegrityState = if (!nativeLoaded) {
            IntegrityState.Failed(FAIL_NATIVE_MISSING)
        } else {
            val code = try {
                nativeVerify(context, readSigningCertificates(context), !fullScanDone)
            } catch (t: Throwable) {
                FAIL_NATIVE_MISSING
            }
            fullScanDone = true
            if (BuildConfig.DEBUG) {
                // Debug builds only: copy these to sanity-check the engine. RELEASE hashes come
                // from `./gradlew :app:printReleaseChecksums` (debug dex/cert differ from release).
                Log.d(TAG, "integrity bits=0x${Integer.toHexString(code)}\n" +
                    runCatching { nativeDebugReport() }.getOrDefault(""))
            }
            val enforced = runCatching { nativeIsEnforced() }.getOrDefault(true)
            if (code == 0 || !enforced) IntegrityState.Verified else IntegrityState.Failed(code)
        }
        _state.value = next
        if (next is IntegrityState.Failed) tamperListeners.forEach { runCatching { it() } }
        next
    }

    /** Raw DER bytes of the CURRENT signing certificate(s), handed to C++ for hashing. */
    @Suppress("DEPRECATION")
    private fun readSigningCertificates(context: Context): Array<ByteArray> = try {
        val pm = context.packageManager
        val pkg = context.packageName
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
            } else {
                pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            val signing = info.signingInfo
            when {
                signing == null -> emptyArray()
                signing.hasMultipleSigners() ->
                    signing.apkContentsSigners.orEmpty().map { it.toByteArray() }.toTypedArray()
                else ->
                    // history: [original, ..., current] — only the current signer counts.
                    signing.signingCertificateHistory.orEmpty().lastOrNull()
                        ?.let { arrayOf(it.toByteArray()) } ?: emptyArray()
            }
        } else {
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures.orEmpty()
                .map { it.toByteArray() }.toTypedArray()
        }
    } catch (e: Exception) {
        emptyArray()
    }

    // Bound in JNI_OnLoad via RegisterNatives (names kept by proguard-rules.pro).
    @JvmStatic private external fun nativeVerify(context: Context, certs: Array<ByteArray>, fullScan: Boolean): Int
    @JvmStatic private external fun nativeIsVerified(): Boolean
    @JvmStatic private external fun nativeIsEnforced(): Boolean
    @JvmStatic private external fun nativeDebugReport(): String
}
