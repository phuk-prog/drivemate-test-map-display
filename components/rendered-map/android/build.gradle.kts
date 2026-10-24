// Renders the published DriveMate RouteLayers and car arrow on an emulator GPU. Versions match the app.
plugins {
    id("com.android.library") version "8.9.2"
    id("org.jetbrains.kotlin.android") version "2.2.20"
}
// Which CarMarker.kt to draw: the app before (baseline) or after (candidate) the 02.04 change.
val phase = providers.gradleProperty("phase").getOrElse("candidate")
require(phase in setOf("baseline", "candidate")) { "phase must be baseline or candidate" }
android {
    namespace = "com.drivemate.render"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["phase"] = phase
    }
    sourceSets {
        // Exact app files (hash-checked by ci/validate.py), never edited here.
        getByName("main") {
            java.setSrcDirs(listOf("../app-src", "../app-src-$phase"))
            res.setSrcDirs(listOf("../app-res"))
        }
        getByName("androidTest") {
            java.setSrcDirs(listOf("../androidTest", "../judge"))
            assets.setSrcDirs(listOf("../fixtures"))
            manifest.srcFile("../androidTest/AndroidManifest.xml")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation("org.maplibre.gl:android-sdk:13.6.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
