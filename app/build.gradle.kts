import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Leaderboard secrets: keep these identical to the ranking server's .env. They are read from
// local.properties (never committed) first, then from a -P/gradle.properties override, so they
// never sit in source control. The defaults are harmless placeholders - the server simply
// rejects submissions until the real values are set. See README.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun secret(name: String, fallback: String): String {
    val fromProject = project.findProperty(name) as String?
    val fromLocal = localProps.getProperty(name)
    return fromProject?.takeIf { it.isNotBlank() }
        ?: fromLocal?.takeIf { it.isNotBlank() }
        ?: fallback
}

val ndApiKey = secret("NIGHTDRIVE_API_KEY", "change-me-api-key")
val ndHmacSecret = secret("NIGHTDRIVE_HMAC_SECRET", "change-me-hmac-secret")

android {
    namespace = "wojtoteka.nightdrive"
    compileSdk = 36

    defaultConfig {
        applicationId = "wojtoteka.nightdrive"
        minSdk = 24
        targetSdk = 36
        versionCode = 22
        versionName = "3.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "API_KEY", "\"$ndApiKey\"")
        buildConfigField("String", "LEADERBOARD_SECRET", "\"$ndHmacSecret\"")
    }

    buildTypes {
        release {
            // TEST-ONLY signing with the debug keystore so sideloaded release builds install
            // without a real key. MUST be replaced with a proper upload keystore before the
            // first Google Play upload (Play rejects debug-signed artifacts).
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "API_BASE_URL", "\"https://aps.wojtoteka.ovh\"")
        }
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
            // Debug also talks to production so sideloaded test builds work on real phones.
            // For local server testing override in gradle.properties:
            //   NIGHTDRIVE_DEBUG_API=http://10.0.2.2:3800   (emulator -> host localhost)
            val debugApi = (project.findProperty("NIGHTDRIVE_DEBUG_API") as String?)
                ?: "https://aps.wojtoteka.ovh"
            buildConfigField("String", "API_BASE_URL", "\"$debugApi\"")
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
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    // Installs the merged baseline profile (ours + Compose's) on first run, so ART can AOT-compile
    // the startup path instead of JIT-ing it every cold start. Compose pulls this in transitively
    // already; the explicit dependency pins the newer version and documents that we rely on it.
    implementation(libs.androidx.profileinstaller)
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    debugImplementation(libs.androidx.ui.tooling)
}
