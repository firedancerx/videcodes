# The 5-Minute Study Crammer (`com.lifehackd.studycrammer`)

An exam panic button for students. Paste messy notes or import a PDF, pick your countdown, and run
four grounded study workflows on Google Gemini (`gemini-2.5-flash`). It is **100% client-side and
BYOK**: users bring their own free Gemini key, which is encrypted on the device. There is no backend.
A native C++ self-checksum makes repacked or re-signed APKs fail closed.

| Item | Choice |
|---|---|
| UI | Kotlin, Jetpack Compose, Material 3, MVVM, dark-first `#C6FF00` on `#121214` |
| SDK | min 26 / target 35 / compile 35, AGP 8.7, Kotlin 2.1 |
| AI | Direct HTTPS + SSE to `generativelanguage.googleapis.com`, key in `x-goog-api-key` header only |
| Key storage | `EncryptedSharedPreferences` + Android Keystore master key (AES-256-GCM); no backup/extraction |
| Anti-tamper | `libintegrity_guard.so` hashes the dex files, itself and the signing certificate, and checks for debuggers and hooking tools |
| Release | R8 full mode, minify + shrink resources, v2+v3 signing, 16 KB-page-aligned native lib |

## Architecture

```
ui/screens ──▶ ui/viewmodel ──▶ data/CramRepository ──▶ data/GeminiStreamingClient ──▶ OkHttp(SSE)
   │                 │                    │                                   │
   │                 │                    └─ data/Prompts (grounding system prompt + JSON schemas)
   │                 └─ data/GroundingVerifier (client-side "receipts check")  │
   │                                                                          ▼
   └─ CrammerRoot gate ◀── security/IntegrityGuard ◀── JNI ── cpp/integrity_guard.cpp
                                  ▲                                          
             security/GeminiAuthInterceptor ── attaches key only if isVerified() ── security/ApiKeyVault
```

### Anti-hallucination, two layers

1. **Prompt layer.** Every call sends the same `systemInstruction`. The notes travel in a `<NOTES>` data block, the model may not use outside facts, missing items must say *"Not covered in your notes"*, and every quote must be verbatim.
2. **Verification layer.** `GroundingVerifier` checks each quoted snippet against the notes on the phone and labels it *found*, *paraphrased* or *not found*. It does not trust the model.

### Anti-tamper engine (`app/src/main/cpp/integrity_guard.cpp`)

| Check | How |
|---|---|
| DEX integrity | Parses the APK zip, inflates every `classes*.dex` (zlib) and compares SHA-256 values. The dex count must match exactly |
| `.so` self-hash | Finds its own `IgBlob` by magic, zeroes those bytes and hashes the rest. This lets the library carry its own checksum |
| Certificate (2 sources) | ① DER from `PackageManager` (`GET_SIGNING_CERTIFICATES`). ② the APK Signing Block v2/v3/v3.1, parsed straight from the file. **Every** block present must match |
| Runtime | `TracerPid`, `Debug.isDebuggerConnected()`, `FLAG_DEBUGGABLE`, Frida/Xposed in `/proc/self/maps` and thread names. `/proc` is read with raw syscalls |
| Response | The failure is sticky. The key is wiped from memory, the interceptor answers with a local 409 and the screen shows a neutral *Error 409* |

Enforcement is a **compile-time** C++ switch (`-DIG_ENFORCE=ON` for release only), so it can't be turned off by flipping `BuildConfig` in smali.

## Build

See **[docs/BUILD_AND_RELEASE.md](docs/BUILD_AND_RELEASE.md)** for the exact two-pass, tamper-locked release commands.

```bash
./gradlew :app:installDebug                        # dev build (engine reports, never blocks)
tools/host-test/run_host_tests.sh                  # native engine + checksum tool tests
gradle -p tools/host-test/jvm-harness test         # Kotlin data-layer tests (no Android SDK)
```

## Known limits

- **Client-side anti-tamper slows attackers down; it does not stop them.** A determined reverser can patch the ARM code or the Kotlin `isVerified()` gate. Because it's BYOK, a pirate costs you a $5 sale, not API spend. If you need real licensing, use [Play Integrity](https://developer.android.com/google/play/integrity). Its verdicts have to be decoded on a server, which this design deliberately avoids.
- **Key rotation (v3 lineage) isn't supported.** Every signing block must carry the one release certificate.
- **`androidx.security:security-crypto` is deprecated upstream** ([releases](https://developer.android.com/jetpack/androidx/releases/security)). It is used here because the spec asks for it, and it still works. To migrate, use a Keystore AES-GCM key directly.
- **The model is a single constant** (`GeminiStreamingClient.DEFAULT_MODEL`). Change it when Google retires 2.5 Flash.
