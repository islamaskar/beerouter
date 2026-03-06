plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.15"
}

version = "1.7.8"

group = "dev.skynomads.beerouter"

kotlin {
    applyDefaultHierarchyTemplate()

    jvm {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        }
    }

    // Add other targets as needed for multiplatform support
    linuxX64()
    macosX64()
    macosArm64()
    mingwX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":core"))
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.maplibre.spatialk:geojson:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-benchmark-runtime:0.4.15")
            }
        }

        val nativeMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-core:3.1.1")
                implementation("io.ktor:ktor-client-curl:3.1.1")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-io-core:0.8.2")
                implementation("com.github.abrensch.brouter:brouter-core:c09ddffad9")
                implementation("com.github.abrensch.brouter:brouter-mapaccess:c09ddffad9")
            }
        }
    }
    sourceSets.jvmMain.dependencies {
        implementation(kotlin("test"))
    }
}


java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    google()
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

benchmark {
    targets {
        register("jvm")
        register("linuxX64")
        register("macosX64")
        register("macosArm64")
        register("mingwX64")
    }
}
