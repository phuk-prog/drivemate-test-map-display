plugins { kotlin("jvm") version "2.1.20"; application }
repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
val phase = providers.gradleProperty("phase").get()
sourceSets.main { kotlin.setSrcDirs(listOf("common", phase, "tests")) }
application { mainClass.set("com.drivemate.app.nav.BatchCTestKt") }

dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") }
