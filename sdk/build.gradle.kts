import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.maven.publish)
}

version = "0.2.0"

kotlin {
    explicitApi()
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.chatwoot.android.sdk"
        compileSdk = 37
        minSdk = 26

        withHostTestBuilder {}
    }

    val xcfName = "ChatwootSDK"
    val xcf = XCFramework(xcfName)
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = xcfName
            isStatic = true
            xcf.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Consumers see Color/Shape/@Composable in the public API surface.
            api(compose.runtime)
            api(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.client.websockets)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.coroutines.core)
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.no.arg)
            implementation(libs.jb.lifecycle.viewmodel.compose)
            implementation(libs.jb.lifecycle.runtime.compose)

            // Attachments: image loading (over the existing Ktor stack) + the file/media picker.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
            implementation(libs.filekit.dialogs.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.multiplatform.settings.test)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.startup)
            // Video + audio playback. iOS uses AVFoundation, which ships with Kotlin/Native.
            implementation(libs.media3.exoplayer)
            implementation(libs.media3.ui)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

// The SDK must never link a microphone API (ADR 0005): on iOS, App Store Connect rejects any app
// binary that references AVAudioRecorder / requestRecordPermission without an
// NSMicrophoneUsageDescription (ITMS-90683), even if the code path never runs; on Android the
// SDK must not contribute RECORD_AUDIO to its hosts. Fails `check` if a recording API reappears
// anywhere under src/ (main and test sources, manifests included).
val verifyNoRecordingApis = tasks.register("verifyNoRecordingApis") {
    group = "verification"
    description = "Fails if SDK sources reference a microphone/recording API (ADR 0005)."
    val sources = fileTree("src") { include("**/*.kt", "**/*.xml") }
    val forbidden = listOf(
        "AVAudioRecorder",
        "requestRecordPermission",
        "recordPermission",
        "AVAudioSessionCategoryPlayAndRecord",
        "AVAudioSessionCategoryRecord",
        "AVAudioApplication",
        "AVCaptureDevice",
        "MediaRecorder",
        "AudioRecord",
        "RECORD_AUDIO",
    )
    inputs.files(sources).withPropertyName("sources")
    inputs.property("forbidden", forbidden)
    doLast {
        val hits = sources.files.sorted().flatMap { file ->
            file.readLines().mapIndexedNotNull { index, line ->
                forbidden.firstOrNull { line.contains(it, ignoreCase = true) }
                    ?.let { "${file.path}:${index + 1}: $it" }
            }
        }
        if (hits.isNotEmpty()) {
            throw GradleException(
                "Recording APIs are not allowed in the SDK (ADR 0005):\n" + hits.joinToString("\n"),
            )
        }
    }
}

tasks.named("check") { dependsOn(verifyNoRecordingApis) }

mavenPublishing {
    // publishToMavenCentral(automaticRelease = true) and signing are configured centrally in
    // the root build.gradle.kts `subprojects { }` block.
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = true))

    pom {
        name.set("Chatwoot SDK")
        description.set("Kotlin Multiplatform chat SDK for Chatwoot — a Compose ChatPage backed by the Chatwoot widget API with live messages over ActionCable.")
        url.set("https://github.com/chatwoot/android-sdk")
        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }
        developers {
            developer {
                id.set("chatwoot")
                name.set("Chatwoot")
                url.set("https://www.chatwoot.com")
            }
        }
        scm {
            url.set("https://github.com/chatwoot/android-sdk")
            connection.set("scm:git:git://github.com/chatwoot/android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/chatwoot/android-sdk.git")
        }
    }
}
