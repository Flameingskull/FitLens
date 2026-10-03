plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    // Navigation's saved routes (#37). Version-locked to Kotlin, so Dependabot moves it in the kotlin-toolchain group.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
}
