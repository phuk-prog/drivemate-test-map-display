plugins { kotlin("jvm") version "2.2.20" }
kotlin { jvmToolchain(17) }
val phase = providers.gradleProperty("phase").getOrElse("baseline")
require(phase in setOf("baseline", "candidate"))
kotlin.sourceSets.main { kotlin.setSrcDirs(listOf("common/src/main/kotlin", "$phase/src/main/kotlin")) }
kotlin.sourceSets.test { kotlin.setSrcDirs(listOf("tests")) }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { useJUnit(); ignoreFailures = true; reports.junitXml.required.set(true) }
