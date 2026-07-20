import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Release signing credentials live in a gitignored keystore.properties (never in
// git — see .gitignore). When it's absent (fresh clone / CI without the secret),
// the release build is simply left unsigned instead of failing, so debug builds
// and `assembleDebug` are unaffected.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

// Single source of truth for the app version — reused by both defaultConfig
// (below) and the artifact name, so the two never drift.
val appVersionName = "3.8"

// Name the built artifacts "Playboard-<version>" instead of the module name
// "app", so the APK is e.g. Playboard-1.5-debug.apk / Playboard-1.5-release.apk.
// archivesName sits before the build-type suffix; this is the modern
// (non-deprecated) replacement for `archivesBaseName` and avoids the
// applicationVariants output-rename API.
base {
    archivesName = "Playboard-$appVersionName"
}

android {
    namespace = "com.org.playboard"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.org.playboard"
        minSdk = 24
        targetSdk = 36
        versionCode = 29
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Production backend, served from the Railway custom domain. Must keep
        // the scheme and the trailing slash — Retrofit's baseUrl() rejects a
        // schemeless value and drops the last path segment without the slash.
        buildConfigField("String", "BASE_URL", "\"https://playboard-prd.cooperbcknd.in/\"")
        // Web-application OAuth Client ID (same value as the backend's
        // GOOGLE_CLIENT_ID env var) — not a secret, safe to version-control.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"230561174351-ruug2ce5qci35d4o7of6kgmgj8shvigg.apps.googleusercontent.com\"",
        )
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            optimization {
                enable = false
            }
            // Only sign when the keystore is present; otherwise the release APK
            // is built unsigned (uninstallable) rather than the build failing.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            // Return defaults for un-mocked android.* framework calls (e.g. the
            // android.util.Log statements in DeviceRegistrar's FCM-unavailable
            // path) instead of throwing "not mocked" in JVM unit tests.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation / architecture (MVVM)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Auth (Google Sign-In via Credential Manager)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    // Networking (custom REST backend)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    // Images (avatar photo loading, with URL fallback support)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // DI
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Local storage (session tokens)
    implementation(libs.androidx.datastore.preferences)

    // Push notifications (Firebase Cloud Messaging)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
