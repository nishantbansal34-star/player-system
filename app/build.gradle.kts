plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.nishant.playersystem"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nishant.playersystem"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // One fixed key so every new build installs over the old one and keeps your progress.
    signingConfigs {
        create("shared") {
            storeFile = file("player-system.keystore")
            storePassword = "playersystem"
            keyAlias = "player"
            keyPassword = "playersystem"
        }
        // Google Play upload key: only present in CI (GitHub secrets), never committed.
        val upFile = System.getenv("UPLOAD_KEYSTORE_FILE")
        if (upFile != null && file(upFile).exists()) {
            create("upload") {
                storeFile = file(upFile)
                storeType = "pkcs12"
                storePassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("UPLOAD_KEY_ALIAS") ?: "upload"
                keyPassword = System.getenv("UPLOAD_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("shared") }
        getByName("release") {
            isMinifyEnabled = false
            // PLAY_BUNDLE=1 signs with the private upload key for Google Play; normal builds keep the shared sideload key.
            signingConfig = if (System.getenv("PLAY_BUNDLE") == "1" && signingConfigs.findByName("upload") != null)
                signingConfigs.getByName("upload") else signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.health.connect:connect-client:1.1.0-alpha11")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
