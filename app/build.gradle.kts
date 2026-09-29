import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// ---- Environment: real env vars (CI) win over env.properties (local, git-ignored) ----
val envProps = Properties().apply {
    val f = rootProject.file("env.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun env(key: String, default: String = ""): String =
    System.getenv(key)?.takeIf { it.isNotBlank() } ?: envProps.getProperty(key)?.takeIf { it.isNotBlank() } ?: default
fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "uz.lokmago.restaurant"
    compileSdk = 35

    defaultConfig {
        applicationId = "uz.lokmago.restaurant"
        minSdk = 26
        targetSdk = 35
        versionCode = env("VERSION_CODE", "1").toInt()   // +1 on every Play release
        versionName = env("VERSION_NAME", "1.0.0")

        // Non-environment-specific constants shared by both variants.
        buildConfigField("String", "SOCKET_PATH", q(env("SOCKET_PATH", "/socket.io/")))
        buildConfigField("String", "API_PATH_PREFIX", q(env("API_PATH_PREFIX", "restaurant/v1/")))
        buildConfigField("String", "API_GATEWAY_HEADER", q(env("API_GATEWAY_HEADER", "x-gateway-password")))
    }

    signingConfigs {
        val ks = env("KEYSTORE_PATH")
        if (ks.isNotEmpty()) {
            create("release") {
                storeFile = file(ks)
                storePassword = env("KEYSTORE_PASSWORD")
                keyAlias = env("KEY_ALIAS")
                keyPassword = env("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        // RELEASE: only the production keys (API_GATEWAY_URL, SOCKET_URL, API_GATEWAY_PASSWORD). The DEBUG_* keys are
        // never read here, so a test server can not leak into a release APK; validateReleaseConfig
        // (gradle/release-validation.gradle.kts) additionally rejects anything that is not https://api.lokmago.uz.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "API_GATEWAY_URL", q(env("API_GATEWAY_URL")))
            buildConfigField("String", "SOCKET_URL", q(env("SOCKET_URL")))   // empty => derived from API_GATEWAY_URL
            buildConfigField("String", "API_GATEWAY_PASSWORD", q(env("API_GATEWAY_PASSWORD")))
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        // DEBUG: installs side by side (applicationIdSuffix). May target a test server via DEBUG_* keys.
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            buildConfigField("String", "API_GATEWAY_URL", q(env("DEBUG_API_GATEWAY_URL", env("API_GATEWAY_URL", "https://restoran-api.lokma.uz/"))))
            buildConfigField("String", "SOCKET_URL", q(env("DEBUG_SOCKET_URL", if (env("DEBUG_API_GATEWAY_URL").isNotEmpty()) "" else env("SOCKET_URL"))))
            buildConfigField("String", "API_GATEWAY_PASSWORD", q(env("DEBUG_API_GATEWAY_PASSWORD", env("API_GATEWAY_PASSWORD"))))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

// ---- Release guard: see gradle/release-validation.gradle.kts ----
extra["lokmago.releaseInputs"] = mapOf(
    "API_GATEWAY_URL" to env("API_GATEWAY_URL"),
    "SOCKET_URL" to env("SOCKET_URL"),
    "SOCKET_PATH" to env("SOCKET_PATH", "/socket.io/"),
    "API_PATH_PREFIX" to env("API_PATH_PREFIX", "restaurant/v1/"),
    "VERSION_CODE" to env("VERSION_CODE"), "VERSION_CODE_RAW" to env("VERSION_CODE"),
    "VERSION_NAME" to env("VERSION_NAME"), "VERSION_NAME_RAW" to env("VERSION_NAME"),
    "KEYSTORE_PATH" to env("KEYSTORE_PATH"), "KEYSTORE_PASSWORD" to env("KEYSTORE_PASSWORD"),
    "KEY_ALIAS" to env("KEY_ALIAS"), "KEY_PASSWORD" to env("KEY_PASSWORD"),
)
extra["lokmago.envFileKeys"] = envProps.stringPropertyNames().toSet()
apply(from = rootProject.file("gradle/release-validation.gradle.kts"))

// Firebase is optional at build time: without google-services.json the app still builds (push disabled).
if (file("google-services.json").exists()) apply(plugin = "com.google.gms.google-services")

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.socketio) { exclude(group = "org.json", module = "json") }

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.datastore)
    implementation(libs.security.crypto)
    implementation(libs.coil.compose)
    implementation(libs.play.update)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
}
