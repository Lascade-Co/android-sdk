import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.vanniktech.maven.publish)
}

version = property("chatwoot.version").toString()

kotlin {
    explicitApi()
    jvmToolchain(21)

    androidLibrary {
        namespace = "com.chatwoot.android.sdk.voice"
        compileSdk = 37
        minSdk = 26

        withHostTestBuilder {}
    }

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ChatwootSDKVoice"
            isStatic = true
            export(project(":sdk"))
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(project(":sdk"))
            api(compose.runtime)
            implementation(libs.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.jb.lifecycle.runtime.compose)
        }
    }
}

mavenPublishing {
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), sourcesJar = true))

    pom {
        name.set("Chatwoot SDK Voice Notes")
        description.set("Opt-in Android and iOS voice-note recorder for the Chatwoot Kotlin Multiplatform SDK.")
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
