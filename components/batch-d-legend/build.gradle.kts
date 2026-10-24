plugins {
 kotlin("jvm") version "2.0.21"
 id("org.jetbrains.compose") version "1.7.3"
 id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
}
repositories { google(); mavenCentral() }
dependencies {
 implementation(compose.desktop.currentOs)
 implementation(compose.material3)
 testImplementation(compose.desktop.uiTestJUnit4)
 testImplementation("junit:junit:4.13.2")
}
tasks.test { testLogging { events("passed", "failed"); showStandardStreams=true } }
