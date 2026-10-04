plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.fitlens.companion"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.fitlens.companion"
        minSdk = 29
        targetSdk = 36
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
    // JVM unit tests with Robolectric (#40): real SQLite without an emulator, run in CI before every release build.
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.testLogging {
                    events("failed")
                    exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
                }
            }
        }
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
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    // Screen structure and state (#37): a saved back stack and per-screen ViewModels.
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Background automatic backups (#34). Local only: WorkManager needs no internet permission.
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    // Phone-only settings (#38): folders, schedules and state that must never travel in a .fitlens backup.
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Unit tests (#40): database migrations on the JVM, no emulator.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    // Screenshot tests (#95): Compose rendered by Robolectric's native graphics and compared by Roborazzi.
    // ui-test-manifest only adds the empty test activity to debug builds; the release APK never contains it.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.40.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// "What's new" (#33): the build's RELEASE_NOTES.md goes into the APK's assets, so the app can show it offline after
// each update. Added through the variant API, so Gradle knows which tasks need it.
abstract class ReleaseNotesAsset : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val notes: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.mkdirs()
        notes.get().asFile.copyTo(File(out, "release_notes.md"), overwrite = true)
    }
}

val releaseNotesAsset = tasks.register<ReleaseNotesAsset>("releaseNotesAsset") {
    notes.set(rootProject.layout.projectDirectory.file("RELEASE_NOTES.md"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(releaseNotesAsset, ReleaseNotesAsset::outputDir)
    }
}
