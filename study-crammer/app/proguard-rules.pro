# =============================================================================
# R8 (full mode) rules for The 5-Minute Study Crammer
# =============================================================================

# --- JNI bridge -------------------------------------------------------------
# integrity_guard.cpp binds natives with RegisterNatives on this exact class
# name and these exact method names/signatures. Everything else may be renamed.
-keep,allowoptimization class com.lifehackd.studycrammer.security.IntegrityGuard {
    native <methods>;
}
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# --- Strip verbose/debug logging from release (defence in depth) -------------
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# --- PDFBox-Android -----------------------------------------------------------
# Loads fonts/CMaps/glyph lists reflectively from its own resources.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**

# --- androidx.security-crypto / Tink -----------------------------------------
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.**
-dontwarn org.joda.time.**

# --- OkHttp (ships its own rules; these silence optional TLS providers) -------
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Crash reports: keep line numbers, hide original source file names --------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
