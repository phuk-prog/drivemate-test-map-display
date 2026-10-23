plugins { kotlin("jvm") version "2.2.20" }
kotlin { jvmToolchain(17) }
val phase = providers.gradleProperty("phase").getOrElse("baseline")
require(phase in setOf("baseline", "candidate"))
sourceSets {
    main { java.setSrcDirs(emptyList<String>()) }
    test { java.setSrcDirs(emptyList<String>()) }
}
kotlin.sourceSets.main { kotlin.setSrcDirs(listOf("../common/src/main/kotlin", "../$phase/src/main/kotlin")) }
kotlin.sourceSets.test { kotlin.setSrcDirs(listOf("../../../batches/A-roundabouts-maps-positioning/tests", "../tests-expanded")) }
sourceSets.test { resources.setSrcDirs(listOf("../../../batches/A-roundabouts-maps-positioning/fixtures")) }
dependencies { testImplementation("junit:junit:4.13.2"); testImplementation("org.json:json:20240303") }
tasks.test { useJUnit(); ignoreFailures = true; reports.junitXml.required.set(true) }
