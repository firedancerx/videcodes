import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Release signing material is read from <root>/keystore.properties (git-ignored).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.isFile) f.inputStream().use { load(it) }
}
val hasReleaseSigning = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.lifehackd.studycrammer"
    compileSdk = 35
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.lifehackd.studycrammer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
        }
        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
                // v2 + v3 only. integrity_guard.cpp requires EVERY present block to carry the
                // release certificate, and reads it straight from the APK Signing Block.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Native engine compiled WITHOUT IG_ENFORCE: computes + reports hashes, never blocks.
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            isJniDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            externalNativeBuild {
                cmake {
                    // Compile-time switch: fail-closed verification. Not flippable via BuildConfig/smali.
                    arguments += listOf("-DIG_ENFORCE=ON")
                }
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // Keep .so files stored (uncompressed, page-aligned) inside the APK: faster load,
        // and integrity_guard.cpp hashes itself straight out of the APK.
        jniLibs { useLegacyPackaging = false }
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/DEPENDENCIES") }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.pdfbox.android)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json) // real org.json on the JVM (Android's is a stub in unit tests)
    testImplementation(libs.okhttp.mockwebserver)
}

// :app:printReleaseChecksums / :app:verifyReleaseChecksums
apply(from = rootProject.file("gradle/integrity-checksums.gradle.kts"))
