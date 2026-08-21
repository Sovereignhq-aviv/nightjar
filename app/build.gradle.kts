plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "org.sovereignhq.nightjar"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.sovereignhq.nightjar"
        // 27 is the floor for setShowWhenLocked / setTurnScreenOn, which the wake-up screen needs
        // in order to appear over the lock screen.
        minSdk = 27
        targetSdk = 35
        // Bumped on every release. The updater compares versionName against the release tag.
        versionCode = 2
        versionName = "1.1.0"
    }

    signingConfigs {
        getByName("debug") {
            // Supplied by CI from the DEBUG_KEYSTORE_B64 secret, and never committed. Every build
            // has to use the same key: Android refuses to install a differently-signed APK over an
            // existing one, and the only way forward would be uninstalling, which deletes every
            // recording. A local build with no key falls back to Gradle's own and cannot upgrade an
            // installed release.
            val supplied = rootProject.file("debug.keystore")
            if (supplied.exists()) {
                storeFile = supplied
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // The updater reads VERSION_NAME from BuildConfig to compare against the release tag.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // YAMNet on-device: Google's AudioSet-trained sound classifier, ~4MB, runs in real time.
    implementation("com.google.mediapipe:tasks-audio:0.10.14")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
