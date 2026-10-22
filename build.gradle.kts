// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    // Screenshot-Tests auf der JVM (Compose über layoutlib gerendert, kein
    // Emulator nötig) - siehe app/src/test/.../ScreenshotTest.kt und README.
    id("app.cash.paparazzi") version "1.3.5" apply false
}
