// Compiles + unit-tests the Android-free Kotlin layer (data/) on a plain JVM —
// no Android SDK needed. Run: gradle -p tools/host-test/jvm-harness test
pluginManagement { repositories { gradlePluginPortal(); mavenCentral() } }
dependencyResolutionManagement { repositories { mavenCentral() } }
rootProject.name = "crammer-jvm-harness"
