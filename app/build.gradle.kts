plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.ember.tv"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ember.tv"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // A fixed personal key, so every new build installs over the old one.
    // Fine for sideloading; don't reuse it for anything you publish.
    signingConfigs {
        create("ember") {
            storeFile = file("ember.keystore")
            storePassword = "embertv"
            keyAlias = "ember"
            keyPassword = "embertv"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("ember") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ember")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
