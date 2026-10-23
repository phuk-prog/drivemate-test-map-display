plugins { kotlin("jvm") version "2.2.20" }
kotlin { jvmToolchain(17) }
dependencies {
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
tasks.test { useJUnit() }
