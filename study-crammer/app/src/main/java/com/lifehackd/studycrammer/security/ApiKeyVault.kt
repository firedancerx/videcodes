package com.lifehackd.studycrammer.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's Gemini API key, encrypted at rest:
 *   - value: AES-256-GCM, key name: AES-256-SIV (Tink), wrapped by an Android Keystore
 *     master key (hardware-backed where the device supports it).
 *   - allowBackup=false + dataExtractionRules => not extractable via adb backup / cloud backup.
 *   - Never logged. The decrypted copy lives in a CharArray that is zeroed on tamper or clear.
 *
 * All methods may touch the Keystore — call them off the main thread.
 */
class ApiKeyVault(context: Context) {
    private val appContext = context.applicationContext
    private val lock = Any()
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var cached: CharArray? = null

    private val _hasKey = MutableStateFlow<Boolean?>(null) // null = not loaded yet
    val hasKey: StateFlow<Boolean?> = _hasKey.asStateFlow()

    fun load() {
        _hasKey.value = try {
            !prefs().getString(PREF_KEY, null).isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }

    /** Decrypted key, or null if none saved OR the APK failed integrity verification. */
    fun getKey(): String? {
        if (!IntegrityGuard.isVerified()) {
            wipeMemory()
            return null
        }
        cached?.let { return String(it) }
        val key = prefs().getString(PREF_KEY, null)?.takeIf { it.isNotEmpty() } ?: return null
        cached = key.toCharArray()
        return key
    }

    fun save(key: String) {
        check(prefs().edit().putString(PREF_KEY, key).commit()) { "Could not persist key" }
        wipeMemory()
        cached = key.toCharArray()
        _hasKey.value = true
    }

    fun clear() {
        prefs().edit().remove(PREF_KEY).commit()
        wipeMemory()
        _hasKey.value = false
    }

    fun wipeMemory() {
        cached?.fill('\u0000')
        cached = null
    }

    /** "AIza••••••••••wXyZ" for the Settings screen. */
    fun maskedKey(): String? {
        val k = prefs().getString(PREF_KEY, null)?.takeIf { it.length >= 8 } ?: return null
        return k.take(4) + "•".repeat(10) + k.takeLast(4)
    }

    private fun prefs(): SharedPreferences =
        prefs ?: synchronized(lock) { prefs ?: open().also { prefs = it } }

    private fun open(): SharedPreferences = try {
        create()
    } catch (e: Exception) {
        // Keystore/Tink state corrupted (e.g. restored from another device, OEM keystore bug):
        // the old ciphertext is unrecoverable by design, so reset and start clean.
        reset()
        create()
    }

    private fun create(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun reset() {
        appContext.deleteSharedPreferences(FILE_NAME)
        runCatching {
            KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        }
    }

    companion object {
        private const val FILE_NAME = "crammer_vault"
        private const val PREF_KEY = "gemini_api_key"
        private val KEY_CHARS = Regex("^[A-Za-z0-9_\\-.]{20,128}$")

        fun sanitize(input: String): String = input.trim().replace(Regex("\\s+"), "")

        /** Cheap local sanity check; the live models.list ping is the real test. */
        fun looksPlausible(key: String): Boolean = KEY_CHARS.matches(key)
    }
}
