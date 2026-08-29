plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.lucas.coverled"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.lucas.coverled"
        // Galaxy Z Flip5 shipped with Android 13; target device runs Android 16 / One UI 8.0.
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-phase2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // Fold-state detection (FoldingFeature)
    implementation("androidx.window:window:1.4.0")
}
