plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// Name the built artifacts "Playboard" instead of the module name "app", so the
// APK is e.g. Playboard-debug.apk / Playboard-release.apk. This is the modern
// (non-deprecated) replacement for `archivesBaseName`.
base {
    archivesName = "Playboard"
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
        versionCode = 6
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Dev backend address. 10.0.2.2 is the Android *emulator's* alias for
        // the host's localhost — it doesn't work on a physical device, which
        // needs the host's actual WiFi LAN IP instead (both device and host
        // must be on the same network). This IP is DHCP-assigned and can
        // change on reconnect — re-check with `ip -4 addr show scope global`
        // on the host if the app stops reaching the backend.
        buildConfigField("String", "BASE_URL", "\"https://playboard-prd.up.railway.app/\"")
        // Web-application OAuth Client ID (same value as the backend's
        // GOOGLE_CLIENT_ID env var) — not a secret, safe to version-control.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"230561174351-ruug2ce5qci35d4o7of6kgmgj8shvigg.apps.googleusercontent.com\"",
        )
    }

    buildTypes {
        release {
            optimization {
                enable = false
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
