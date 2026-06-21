fun Project.trimmedGradleProperty(name: String): String? =
    providers.gradleProperty(name).orNull?.trim()?.takeIf { it.isNotEmpty() }

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlinx.benchmark") version "0.4.15"
    id("maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.36.0"
}

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

    sourceSets {
        val commonMain by getting {
            dependencies {
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
                implementation("io.ktor:ktor-client-core:3.1.1")
                implementation("io.ktor:ktor-client-cio:3.1.1")
                implementation("com.github.abrensch.brouter:brouter-core:c09ddffad93f1d1667e3bbbf9977156447c3ec6f")
                implementation("com.github.abrensch.brouter:brouter-mapaccess:c09ddffad93f1d1667e3bbbf9977156447c3ec6f")
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

val routingTestGeneratedSegmentsDir =
    rootProject.layout.buildDirectory.dir("routing-test/tuebingen-regbez/segments4")

tasks.named<Test>("jvmTest") {
    dependsOn(":generator:generateRoutingTestRd5WithPois")
    systemProperty(
        "beerouter.generatedSegmentDir",
        routingTestGeneratedSegmentsDir.get().asFile.absolutePath
    )
}

tasks.register<Test>("routingParityMiniTest") {
    description = "Runs the fast BRouter parity smoke test for pre-commit checks"
    group = "verification"
    dependsOn(":generator:generateRoutingTestRd5WithPois")
    val jvmTestTask = tasks.named<Test>("jvmTest").get()
    testClassesDirs = jvmTestTask.testClassesDirs
    classpath = jvmTestTask.classpath
    systemProperty(
        "beerouter.generatedSegmentDir",
        routingTestGeneratedSegmentsDir.get().asFile.absolutePath
    )
    filter {
        includeTestsMatching("dev.skynomads.beerouter.router.RoutingTest.routingParityMiniTest")
    }
}

mavenPublishing {
    val requestedTasks = gradle.startParameter.taskNames
    val hasInMemorySigningKey = project.trimmedGradleProperty("signingInMemoryKey") != null
    val isNonLocalPublish = requestedTasks.any {
        val taskName = it.substringAfterLast(':')
        taskName.equals("publish", ignoreCase = true) ||
                (taskName.contains("publish", ignoreCase = true) &&
                        !taskName.contains("mavenlocal", ignoreCase = true))
    }

    publishToMavenCentral(automaticRelease = true)
    if (isNonLocalPublish || hasInMemorySigningKey) {
        signAllPublications()
    }

    pom {
        name.set("Beerouter")
        description.set("Offline OpenStreetMap routing engine as a Kotlin Multiplatform library.")
        inceptionYear.set("2025")
        url.set("https://codeberg.org/jgillich/beerouter")

        licenses {
            license {
                name.set("Mozilla Public License Version 2.0")
                url.set("https://www.mozilla.org/en-US/MPL/2.0/")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("jgillich")
                name.set("Jakob Gillich")
                url.set("https://codeberg.org/jgillich")
            }
        }

        scm {
            url.set("https://codeberg.org/jgillich/beerouter")
            connection.set("scm:git:https://codeberg.org/jgillich/beerouter.git")
            developerConnection.set("scm:git:ssh://git@codeberg.org/jgillich/beerouter.git")
        }
    }
}

extensions.configure<SigningExtension> {
    val inMemoryKey = project.trimmedGradleProperty("signingInMemoryKey")
    if (inMemoryKey != null) {
        useInMemoryPgpKeys(
            project.trimmedGradleProperty("signingInMemoryKeyId"),
            inMemoryKey,
            project.trimmedGradleProperty("signingInMemoryKeyPassword"),
        )
    } else {
        // Gradle's gpg signatory reads signing.gnupg.* project properties verbatim.
        // Normalize them here so blank values from user gradle.properties do not turn
        // into a malformed executable like `gpg     `.
        project.extensions.extraProperties["signing.gnupg.executable"] =
            project.trimmedGradleProperty("signing.gnupg.executable") ?: "gpg"
        project.trimmedGradleProperty("signing.gnupg.keyName")?.let {
            project.extensions.extraProperties["signing.gnupg.keyName"] = it
        }
        project.trimmedGradleProperty("signing.gnupg.passphrase")?.let {
            project.extensions.extraProperties["signing.gnupg.passphrase"] = it
        }
        project.trimmedGradleProperty("signing.gnupg.homeDir")?.let {
            project.extensions.extraProperties["signing.gnupg.homeDir"] = it
        }
        project.trimmedGradleProperty("signing.gnupg.optionsFile")?.let {
            project.extensions.extraProperties["signing.gnupg.optionsFile"] = it
        }
        useGpgCmd()
    }
}
