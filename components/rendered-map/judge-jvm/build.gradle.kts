// Plain-JVM run of the independent render judge and its negative controls (no Android, no app code).
plugins { kotlin("jvm") version "2.2.20" }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
sourceSets {
    main { kotlin.srcDir("../judge") }
    test { kotlin.srcDir("../judge-tests") }
}
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.test { testLogging { events("passed", "failed"); exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL } }
