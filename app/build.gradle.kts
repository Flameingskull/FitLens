plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.fitlens.companion"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fitlens.companion"
        minSdk = 29
        targetSdk = 35
        // Each cloud build gets a higher version so it installs as an update over the previous one.
        // FITLENS_BUILD is set by CI (run number + offset); it must only ever increase.
        val build = (System.getenv("FITLENS_BUILD") ?: System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionCode = build
        versionName = "1.0.$build"
    }

    // The release signing key is never committed. CI writes it from encrypted secrets for main-branch
    // releases. Pull requests and local builds use the debug build, signed with the standard debug key.
    val keystorePath = System.getenv("FITLENS_KEYSTORE")
    val hasReleaseKey = keystorePath != null && file(keystorePath).exists()
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("FITLENS_STORE_PASSWORD")
                keyAlias = System.getenv("FITLENS_KEY_ALIAS")
                keyPassword = System.getenv("FITLENS_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

// The Kotlin side of the toolchain. `kotlinOptions { jvmTarget = "17" }` was removed in Kotlin 2.4 and is now a
// hard error, so the JVM target lives on the `compilerOptions` DSL instead (#67). The Android `compileOptions`
// block above is a separate, still-current DSL and stays where it is.
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Background automatic backups (#34). Local only: WorkManager needs no internet permission.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
