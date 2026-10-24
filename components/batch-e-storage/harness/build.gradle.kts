plugins { kotlin("jvm") version "2.1.20" }
repositories { mavenCentral() }
dependencies { testImplementation("junit:junit:4.13.2") }
kotlin { jvmToolchain(17) }
val phase = providers.gradleProperty("phase").get()
sourceSets { main { kotlin.srcDirs("../"+phase, "../support") }; test { kotlin.srcDirs("../tests") } }
tasks.test { useJUnit(); reports.junitXml.required.set(true) }
