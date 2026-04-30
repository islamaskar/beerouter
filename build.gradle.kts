plugins {
    kotlin("multiplatform") apply false
    id("org.jetbrains.kotlinx.benchmark") version "0.4.15" apply false
    id("com.vanniktech.maven.publish") version "0.36.0" apply false
}

import java.util.Properties

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

subprojects {
    group = rootProject.group
    version = rootProject.version

    tasks.configureEach {
        if (name.startsWith("transform") && name.endsWith("DependenciesMetadata")) {
            // These transformed klibs live under build/ and can disappear between
            // runs, which leaves Kotlin metadata compile tasks with stale input paths.
            outputs.upToDateWhen { false }
        }
    }
}

tasks.register("downloadPlanetOsmPbf") {
    group = "generator"
    description = "Download or update the latest OSM planet PBF through the :generator task."
    dependsOn(":generator:downloadPlanetOsmPbf")
}

tasks.register("generatePlanetRd5") {
    group = "generator"
    description = "Generate BRouter-compatible rd5 files for a planet PBF through the :generator task."
    dependsOn(":generator:generatePlanetRd5")
}

tasks.register("patchRelease") {
    group = "publishing"
    description = "Bump VERSION_NAME patch version and commit gradle.properties."

    doLast {
        val propertiesFile = rootProject.layout.projectDirectory.file("gradle.properties").asFile
        val properties = Properties().apply {
            propertiesFile.inputStream().use(::load)
        }
        val currentVersion = properties.getProperty("VERSION_NAME")
            ?: error("VERSION_NAME is missing from gradle.properties")
        val parts = currentVersion.split(".")
        require(parts.size == 3 && parts.all { it.toIntOrNull() != null }) {
            "VERSION_NAME must use MAJOR.MINOR.PATCH, got '$currentVersion'"
        }
        val nextVersion = listOf(parts[0], parts[1], parts[2].toInt() + 1).joinToString(".")
        val updatedProperties = propertiesFile.readText()
            .replace(Regex("(?m)^VERSION_NAME=.*$"), "VERSION_NAME=$nextVersion")

        propertiesFile.writeText(updatedProperties)
        providers.exec { commandLine("git", "add", "gradle.properties") }.result.get()
        providers.exec { commandLine("git", "commit", "-m", "release: v$nextVersion") }.result.get()
    }
}
