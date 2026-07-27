plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "de.vip.liveuebersetzer"
    compileSdk = 34

    defaultConfig {
        applicationId = "de.vip.liveuebersetzer"
        // Hartes Requirement: minSdk 26 (siehe README) - unabhaengig von der
        // ML-Kit-GenAI-Speech-Recognition-Basic-Mode-Untergrenze (API 31),
        // die SpeechEngine.isLiveSupported() separat prueft.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // Fester, eingecheckter Debug-Keystore (Standard-Praxis fuer Debug-APKs, die
    // wiederholt an Tester verteilt werden): ohne diesen erzeugt jede frische
    // CI-Umgebung einen eigenen zufaelligen Debug-Key, wodurch neuere Builds nicht
    // ueber aeltere installiert werden koennen ("App nicht installiert" wegen
    // Signatur-Konflikt), solange die alte Version nicht erst deinstalliert wird.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // ML Kit Translate - stabil/GA, rein on-device (keine Cloud-API).
    implementation("com.google.mlkit:translate:17.0.3")

    // ML Kit GenAI Speech Recognition - Alpha, rein on-device (Basic-Modus).
    implementation("com.google.mlkit:genai-speech-recognition:1.0.0-alpha1")

    // Vosk (Apache-2.0) - gebuendelte Offline-Spracherkennung fuer Ukrainisch/
    // Arabisch, da weder ML Kit noch die Android-Systemerkennung diese beiden
    // Sprachen zuverlaessig abdecken (siehe VoskSpeechEngine-Kdoc). Zieht
    // net.java.dev.jna:jna:5.18.1 (aar) transitiv ueber die eigene POM.
    implementation("com.alphacephei:vosk-android:0.3.75")

    // Bruecke zwischen Play-Services-Task und Kotlin-Coroutines fuer
    // TranslationEngine/SpeechEngine (Task.await()).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
