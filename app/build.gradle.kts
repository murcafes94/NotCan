import java.net.URI
import java.security.MessageDigest
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val notcanArm64Only = providers.gradleProperty("notcanArm64Only").orNull == "true"
val sherpaAar = layout.projectDirectory.file("libs/sherpa-onnx-1.13.6.aar").asFile
val fetchSherpaAar by tasks.registering {
    outputs.file(sherpaAar)
    doLast {
        val expectedSha = "0012d9a28f15bd6fb966b62b70a75da3990512fdccce28b83098248ce4be1698"
        if (!sherpaAar.exists()) {
            sherpaAar.parentFile.mkdirs()
            val url = URI("https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.6/sherpa-onnx-1.13.6.aar").toURL()
            url.openStream().use { input -> sherpaAar.outputStream().use { output -> input.copyTo(output) } }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        sherpaAar.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n <= 0) break
                digest.update(buffer, 0, n)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        check(actual == expectedSha) { "sherpa-onnx AAR SHA-256 mismatch: $actual" }
    }
}

val testKeystorePayload = rootProject.file("ci/notcan-test.keystore.b64")
val testKeystoreFile = layout.buildDirectory.file("signing/notcan-test.keystore").get().asFile
val prepareTestKeystore by tasks.registering {
    inputs.file(testKeystorePayload)
    outputs.file(testKeystoreFile)
    doLast {
        testKeystoreFile.parentFile.mkdirs()
        testKeystoreFile.writeBytes(Base64.getMimeDecoder().decode(testKeystorePayload.readText().trim()))
    }
}

tasks.matching { it.name == "preDebugBuild" || it.name == "preReleaseBuild" }.configureEach {
    dependsOn(fetchSherpaAar)
}
tasks.matching { it.name == "preDebugBuild" }.configureEach {
    dependsOn(prepareTestKeystore)
}

android {
    namespace = "com.notcan.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.notcan.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 41
        versionName = "0.8.18.2"
        if (notcanArm64Only) {
            ndk { abiFilters += listOf("arm64-v8a") }
        }
        buildConfigField("String", "SUPABASE_URL", "\"https://xpkxvhnttquvnbcfnbck.supabase.co\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"sb_publishable_wTChkG7BwOd00sz67lOWDQ_dZ8XdYpV\"")
    }

    signingConfigs {
        create("notcanTest") {
            storeFile = testKeystoreFile
            storePassword = "notcan-test"
            keyAlias = "notcan-test"
            keyPassword = "notcan-test"
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("notcanTest")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")
    val roomVersion = "2.6.1"

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    implementation("androidx.work:work-runtime:2.11.2")

    implementation("dev.ffmpegkit-maintained:whisper-android:1.0.0")
    implementation(files(sherpaAar))
    implementation("org.apache.commons:commons-compress:1.27.1")
    implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc10")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation(project(":llama-android"))

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
