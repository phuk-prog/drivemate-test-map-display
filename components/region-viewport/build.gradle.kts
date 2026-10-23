plugins { kotlin("jvm") version "2.2.20" }
repositories { mavenCentral() }
dependencies {
    implementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
kotlin { jvmToolchain(17) }
tasks.test { useJUnit(); testLogging { events("passed", "failed", "skipped") } }
