plugins {
    kotlin("jvm")
}

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":core"))
    implementation("androidx.collection:collection-jvm:1.5.0")
    implementation("org.openstreetmap.osmosis:osmosis-osm-binary:0.48.3")

    testImplementation(kotlin("test"))
}

tasks.withType<Test> {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
