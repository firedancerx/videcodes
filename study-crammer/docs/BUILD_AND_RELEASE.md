# Build & Release Runbook — tamper-locked binary

The release flow is **two-pass**: build once to learn the hashes, bake them into the
native library, build again. Only `libintegrity_guard.so` changes in pass 2, and the
`.so` hashes itself with its own expected-hash blob zeroed, so pass-2 hashes equal pass-1
hashes. `verifyReleaseChecksums` proves it before you ship.

## 0. Prerequisites (once)

```bash
# JDK 17+, Android SDK with:
sdkmanager "platforms;android-35" "build-tools;35.0.0" "cmake;3.22.1" "ndk;27.0.12077973"
export ANDROID_HOME=~/Android/Sdk          # or set sdk.dir in local.properties
```

## 1. Create the release keystore (once — back it up offline!)

```bash
mkdir -p release
keytool -genkeypair -v \
  -keystore release/crammer-release.jks -storetype PKCS12 \
  -alias crammer -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=LifeHackd, O=LifeHackd, C=US"
cp keystore.properties.example keystore.properties   # then edit both passwords
```

`release/`, `*.jks` and `keystore.properties` are git-ignored.

## 2. Pass 1 — build and extract hashes

```bash
./gradlew :app:assembleRelease
./gradlew :app:printReleaseChecksums
```

Output (example):

```
Signing cert   : 3A:9F:...:C1   [APK Signing Block (v2/v3)]
classes.dex    : 5d1c…
so[arm64-v8a]  : 91aa…
so[armeabi-v7a]: 0be3…
so[x86_64]     : 77f0…
so[x86]        : e21d…
Wrote app/src/main/cpp/expected_hashes.h
```

`expected_hashes.h` now holds fresh random XOR shares (`expected = A ^ B`) — nothing greppable
ends up in the binary. Use `-PintegrityWriteHeader=false` to print the snippet instead of writing it.

**Cross-check independently (optional, recommended once):**

```bash
APK=app/build/outputs/apk/release/app-release.apk
unzip -p $APK classes.dex | sha256sum                                   # == classes.dex line
keytool -list -v -keystore release/crammer-release.jks -alias crammer | grep SHA256
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs $APK | grep SHA-256
```

(The `.so` hash will NOT match a plain `sha256sum` — by design it is computed with the
736-byte IgBlob region zeroed.)

## 3. Pass 2 — build the tamper-locked binary and verify

```bash
./gradlew :app:assembleRelease
./gradlew :app:verifyReleaseChecksums      # must print: OK — N hashes match
```

If verification fails for `so.*` only, something made the native build non-deterministic
(different NDK, absolute paths changed, build-id re-enabled) — rerun pass 1 + 2 on the same machine.

## 4. Smoke-test on a device

```bash
adb install -r app/build/outputs/apk/release/app-release.apk     # works normally

# Tamper test: re-sign with a different key => "Integrity Verification Failed (Error 409)"
keytool -genkeypair -keystore /tmp/evil.jks -storepass evilpass -keypass evilpass \
  -alias evil -keyalg RSA -keysize 2048 -validity 1 -dname "CN=Evil"
$ANDROID_HOME/build-tools/35.0.0/apksigner sign --ks /tmp/evil.jks --ks-pass pass:evilpass \
  --out /tmp/resigned.apk app/build/outputs/apk/release/app-release.apk
adb uninstall com.lifehackd.studycrammer && adb install /tmp/resigned.apk
```

## 5. Google Play (AAB + Play App Signing)

With Play App Signing, Google re-signs your app — the device sees **Google's app-signing
certificate**, not your upload key. Bake that one in:

1. Play Console → *Test and release → App integrity → App signing* → copy **App signing key certificate SHA-256**.
2. Build pass 1 and a universal APK to hash:

```bash
./gradlew :app:bundleRelease
java -jar bundletool.jar build-apks --mode=universal \
  --bundle=app/build/outputs/bundle/release/app-release.aab --output=/tmp/crammer.apks \
  --ks=release/crammer-release.jks --ks-key-alias=crammer
unzip -p /tmp/crammer.apks universal.apk > /tmp/universal.apk
./gradlew :app:printReleaseChecksums -PintegrityApk=/tmp/universal.apk \
  -PintegrityCertSha256=<PLAY_APP_SIGNING_SHA256>
```

3. Pass 2: `./gradlew :app:bundleRelease`, rebuild the universal APK, run
   `verifyReleaseChecksums -PintegrityApk=/tmp/universal.apk` and upload the AAB.

A locally-signed APK built this way will (correctly) fail on-device, because it carries your
upload key rather than Google's. Do **not** also enable Play's "automatic protection" — it
rewrites the dex and would trip this engine.

## 6. Debug builds

`./gradlew :app:installDebug && adb logcat -s IntegrityGuard` prints the debug build's
dex / .so / cert hashes and failure bits. Debug builds compile the engine **without**
`IG_ENFORCE`, so they report but never block. Debug hashes are not release hashes.

## 7. Host tests (no Android SDK needed — CI friendly)

```bash
tools/host-test/run_host_tests.sh                 # native engine + Gradle checksum tool, 25 assertions
gradle -p tools/host-test/jvm-harness test        # Kotlin data layer, 21 tests incl. SSE streaming
```

## 8. Continuous integration

`.github/workflows/study-crammer-ci.yml` runs on every PR/push touching `study-crammer/`:
host tests, then a real Android build (`assembleDebug`, `testDebugUnitTest`, unsigned
`assembleRelease` with R8 + `IG_ENFORCE`). APKs are uploaded as the `study-crammer-apks` artifact.
Free and unlimited on public repos.
