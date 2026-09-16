import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localConfig = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val mapboxToken = providers.environmentVariable("MAPBOX_ACCESS_TOKEN")
    .orElse(localConfig.getProperty("MAPBOX_ACCESS_TOKEN", "")).get()

android {
    namespace = "com.example.miprimeraapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.miprimeraapp"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        resValue("string", "mapbox_access_token", mapboxToken)
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.08.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("com.mapbox.maps:android-ndk27:11.30.1")
    testImplementation("junit:junit:4.13.2")
}
