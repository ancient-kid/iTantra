plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    // Namespace is kept as com.itantra.stt so that the R class and the generated
    // ViewBinding classes land where the (unmodified) STT/TTS test-bench sources
    // already expect them. The user-visible application id is com.itantra.mvp.
    namespace = "com.itantra.stt"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.itantra.mvp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        ndk {
            // arm64 for modern phones, armeabi-v7a for older/budget phones that run a
            // 32-bit userspace (common on Android Go and low-end Android 12 devices,
            // even on 64-bit chips). Without v7a those phones reject the APK with
            // INSTALL_FAILED_NO_MATCHING_ABIS. x86/x86_64 are still dropped.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    androidResources {
        // ONNX checkpoints and token vocabularies must stay uncompressed so that
        // sherpa-onnx can mmap them straight out of the APK.
        noCompress.addAll(listOf("onnx", "txt"))
    }

    buildFeatures {
        viewBinding = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    // sherpa-onnx (STT + TTS native runtime)
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar", "*.aar"))))

    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-process:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // bitchat mesh transport
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Model pack downloads (resumable, checksum-verified)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
