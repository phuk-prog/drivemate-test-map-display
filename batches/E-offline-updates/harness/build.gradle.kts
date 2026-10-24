plugins { kotlin("jvm") version "2.2.20" }
repositories { mavenCentral() }
val phase = providers.gradleProperty("phase").getOrElse("baseline")
require(phase in setOf("baseline", "candidate"))
kotlin { jvmToolchain(17) }
kotlin.sourceSets.main { kotlin.setSrcDirs(listOf("../$phase")) }
kotlin.sourceSets.test { kotlin.setSrcDirs(listOf("../tests")) }
dependencies { implementation("org.json:json:20240303"); testImplementation("junit:junit:4.13.2") }
tasks.test { useJUnit(); ignoreFailures = true; reports.junitXml.required.set(true) }
