plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.homescreen.test"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.homescreen.test"
        minSdk = 31 // Android 12, matches the TCL TV
        targetSdk = 35
        versionCode = 5
        versionName = "0.5"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Intentionally no AndroidX/library dependencies: pure framework APIs keep the
// APK tiny and memory use low on the ~1.9 GB TV.

// Unit-test dependency only; the launcher still has no runtime library dependencies.
dependencies { testImplementation("junit:junit:4.13.2") }
