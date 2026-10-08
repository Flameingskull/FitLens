plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    // Navigation's saved routes (#37). Version-locked to Kotlin, so Dependabot moves it in the kotlin-toolchain group.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // Room's code generator (#36). Version-locked to Kotlin (2.0.21-x), so it moves in the kotlin-toolchain group.
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}
