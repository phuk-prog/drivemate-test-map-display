plugins {
 id("com.android.library") version "8.13.0"
 kotlin("android") version "2.2.20"
 id("org.jetbrains.kotlin.plugin.compose") version "2.2.20"
}
android {
 namespace="com.drivemate.storage.publictest"
 compileSdk=36
 defaultConfig { minSdk=26 }
 compileOptions { sourceCompatibility=JavaVersion.VERSION_17;targetCompatibility=JavaVersion.VERSION_17 }
 buildFeatures { compose=true }
 testOptions { unitTests.isIncludeAndroidResources=true }
 sourceSets["main"].java.setSrcDirs(listOf("../android-sources","../android-boundaries"))
 sourceSets["test"].java.setSrcDirs(listOf("../android-tests"))
}
dependencies {
 implementation("androidx.work:work-runtime-ktx:2.10.1")
 implementation("androidx.compose.material3:material3:1.3.2")
 implementation("androidx.compose.ui:ui:1.7.8")
 testImplementation("androidx.work:work-testing:2.10.1")
 testImplementation("androidx.test:core:1.6.1")
 testImplementation("org.robolectric:robolectric:4.14.1")
 testImplementation("junit:junit:4.13.2")
}
