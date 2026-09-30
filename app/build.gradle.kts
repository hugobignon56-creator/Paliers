plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.hugo.paliers"
    compileSdk = 34

    defaultConfig {
        applicationId = "fr.hugo.paliers"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "2.0"
    }
    // Clé fixe : chaque nouvelle version s'installe par-dessus l'ancienne.
    signingConfigs {
        create("fixe") {
            storeFile = file("paliers.keystore")
            storePassword = "android"
            keyAlias = "paliers"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixe") }
        release { isMinifyEnabled = false }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
