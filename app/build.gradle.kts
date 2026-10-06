plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.millquarterlabs.backbuttonmapper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.millquarterlabs.backbuttonmapper"
        // Wear OS 3 (API 30) and newer; the Galaxy Watch Ultra 2 runs a much newer release.
        minSdk = 30
        targetSdk = 35
        versionCode = 11
        versionName = "1.10"
    }

    signingConfigs {
        // A fixed debug key is committed so every CI build can be installed over the previous one
        // without uninstalling first (which would also switch the accessibility service off).
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
            signingConfig = signingConfigs.getByName("debug")
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
