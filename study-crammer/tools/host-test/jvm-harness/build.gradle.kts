plugins { kotlin("jvm") version "2.1.0" }

val app = rootDir.resolve("../../../app/src")
sourceSets {
    main { kotlin.srcDir(app.resolve("main/java/com/lifehackd/studycrammer/data")) }
    test { kotlin.srcDir(app.resolve("test/java")) }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
kotlin { jvmToolchain(21) }
// PdfExtractor.kt (android.*) is excluded; everything else in data/ must stay Android-free.
sourceSets.main { kotlin.exclude("**/PdfExtractor.kt") }
tasks.test { testLogging { events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
