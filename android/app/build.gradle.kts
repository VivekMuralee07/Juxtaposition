plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.juxtaposition.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.juxtaposition.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 18
        versionName = "1.15.0"
    }

    // Release signing: the keystore is NEVER in the repo. Provide it through environment variables
    // (KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD), locally or as GitHub Actions secrets.
    // Without them the build falls back to the debug key, which is fine for testing but must not be distributed:
    // Android only updates an installed app when the new APK has the same signing key.
    val keystorePath = System.getenv("KEYSTORE_PATH")
    val haveReleaseKey = keystorePath != null && file(keystorePath).exists()
    signingConfigs {
        if (haveReleaseKey) {
            create("release") {
                storeFile = file(keystorePath!!)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS") ?: "nearbyeats"
                keyPassword = System.getenv("KEY_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (haveReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    if (!haveReleaseKey) {
        logger.warn("WARNING: no release keystore (KEYSTORE_PATH). The release APK is signed with the DEBUG key: do not distribute it.")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
