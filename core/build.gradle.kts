plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.15"
    id("maven-publish")
}

group = "dev.skynomads.beerouter"

repositories {
    google()
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

kotlin {
    explicitApi()

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
        testRuns.configureEach {
            executionTask.configure {
                javaLauncher.set(javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(21))
                })
            }
        }
    }

    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.maplibre.spatialk:geojson:0.6.1")
                implementation("org.maplibre.spatialk:turf:0.6.1")
                implementation("org.maplibre.spatialk:units:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
                implementation("androidx.collection:collection:1.5.0")
            }
        }

        val jvmMain by getting

        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("com.github.abrensch.brouter:brouter-core:c09ddffad9")
                implementation("com.github.abrensch.brouter:brouter-mapaccess:c09ddffad9")
                implementation("com.github.pieterclaerhout:kotlin-yellowduck-gpx:34fb8865e5")
                implementation("ch.qos.logback:logback-classic:1.5.18")
            }
        }
    }
}

tasks.withType<JavaCompile> {
    options.release.set(21)
}

tasks.withType<Test> {
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
