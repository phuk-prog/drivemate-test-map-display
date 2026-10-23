plugins {
    id("com.android.library") version "8.9.2"
    id("org.jetbrains.kotlin.android") version "2.2.20"
}
val phase = providers.gradleProperty("phase").getOrElse("baseline")
require(phase in setOf("baseline","candidate"))
android {
    namespace = "com.drivemate.components.sensors"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    sourceSets {
        getByName("main").java.setSrcDirs(listOf(phase))
        getByName("test").java.setSrcDirs(listOf("tests"))
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("org.mockito:mockito-core:5.15.2")
}
tasks.withType<Test>().configureEach { ignoreFailures=true }
