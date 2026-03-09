import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.tasks.Jar
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

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
        exceptionFormat = TestExceptionFormat.FULL
    }
}

val mainSourceSet = the<SourceSetContainer>()["main"]

fun resolveConfiguredFile(path: String): File {
    val candidate = File(path)
    return if (candidate.isAbsolute) candidate else rootProject.file(path)
}

fun configuredFileProperty(name: String, defaultPath: String): File =
    resolveConfiguredFile(providers.gradleProperty(name).orNull ?: defaultPath)

fun configuredStringProperty(name: String): String? = providers.gradleProperty(name).orNull

fun findExecutable(name: String): File? {
    val pathEntries = System.getenv("PATH")
        ?.split(File.pathSeparatorChar)
        .orEmpty()
    val extensions = if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
        System.getenv("PATHEXT")
            ?.split(';')
            ?.filter(String::isNotBlank)
            ?: listOf(".exe", ".bat", ".cmd")
    } else {
        listOf("")
    }
    for (entry in pathEntries) {
        val dir = File(entry)
        for (extension in extensions) {
            val executable = File(dir, if (name.endsWith(extension, ignoreCase = true)) name else name + extension)
            if (executable.isFile && executable.canExecute()) {
                return executable
            }
        }
    }
    return null
}

tasks.register("downloadPlanetOsmPbf") {
    group = "mapcreator"
    description = "Download the latest OSM planet PBF, or update an existing copy with osmupdate when available."

    val defaultPlanetFile = layout.buildDirectory.file("planet/planet-latest.osm.pbf")
    outputs.file(defaultPlanetFile)

    doLast {
        val planetFile = configuredFileProperty(
            "mapcreator.planetFile",
            defaultPlanetFile.get().asFile.absolutePath,
        )
        val planetUri = URI(
            configuredStringProperty("mapcreator.planetUrl")
                ?: "https://planet.openstreetmap.org/pbf/planet-latest.osm.pbf",
        )
        planetFile.parentFile.mkdirs()

        val osmUpdate = findExecutable("osmupdate")
        if (planetFile.exists() && osmUpdate != null) {
            val backupFile = File(planetFile.parentFile, "${planetFile.name}.old")
            Files.move(planetFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            try {
                val process = ProcessBuilder(
                    osmUpdate.absolutePath,
                    backupFile.absolutePath,
                    planetFile.absolutePath,
                )
                    .inheritIO()
                    .start()
                check(process.waitFor() == 0) {
                    "osmupdate failed with exit code ${process.exitValue()}"
                }
                backupFile.delete()
                logger.lifecycle("Updated planet file with osmupdate: ${planetFile.absolutePath}")
            } catch (e: Exception) {
                Files.move(backupFile.toPath(), planetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                throw e
            }
            return@doLast
        }

        val connection = planetUri.toURL().openConnection()
        if (planetFile.exists()) {
            connection.ifModifiedSince = planetFile.lastModified()
        }
        if (connection is HttpURLConnection) {
            connection.instanceFollowRedirects = true
        }
        connection.connect()

        if (connection is HttpURLConnection && connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
            logger.lifecycle("Planet file is current: ${planetFile.absolutePath}")
            return@doLast
        }

        val tempFile = File(planetFile.parentFile, "${planetFile.name}.download")
        connection.getInputStream().use { input ->
            tempFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Files.move(tempFile.toPath(), planetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        if (connection.lastModified > 0L) {
            planetFile.setLastModified(connection.lastModified)
        }
        logger.lifecycle("Downloaded planet file to ${planetFile.absolutePath}")
    }
}

tasks.register<JavaExec>("generatePlanetRd5") {
    group = "mapcreator"
    description = "Generate BRouter-compatible rd5 files for a planet PBF using the Beerouter Kotlin mapcreator pipeline."

    dependsOn("classes", "downloadPlanetOsmPbf")

    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("dev.skynomads.beerouter.mapcreator.PlanetRd5Generator")

    val defaultPlanetFile = layout.buildDirectory.file("planet/planet-latest.osm.pbf")
    val defaultWorkDir = layout.buildDirectory.dir("planet/work")
    val defaultOutputDir = layout.buildDirectory.dir("planet/segments4")

    systemProperty("avoidMapPolling", "true")
    systemProperty(
        "deletetmpfiles",
        providers.gradleProperty("mapcreator.deleteTmpFiles").orElse("true").get(),
    )

    doFirst {
        val planetFile = configuredFileProperty(
            "mapcreator.planetFile",
            defaultPlanetFile.get().asFile.absolutePath,
        )
        val workDir = configuredFileProperty(
            "mapcreator.workDir",
            defaultWorkDir.get().asFile.absolutePath,
        )
        val outputDir = configuredFileProperty(
            "mapcreator.outputDir",
            defaultOutputDir.get().asFile.absolutePath,
        )
        val profileDir = configuredFileProperty(
            "mapcreator.profileDir",
            rootProject.file("misc/profiles2").absolutePath,
        )
        val srtmDir = configuredStringProperty("mapcreator.srtmDir")?.let(::resolveConfiguredFile)
        val srtmFallbackDir = configuredStringProperty("mapcreator.srtmFallbackDir")?.let(::resolveConfiguredFile)
        val dbTagInfo = configuredStringProperty("mapcreator.dbTagInfo").orEmpty()

        args = listOf(
            planetFile.absolutePath,
            workDir.absolutePath,
            outputDir.absolutePath,
            profileDir.absolutePath,
            srtmDir?.absolutePath.orEmpty(),
            srtmFallbackDir?.absolutePath.orEmpty(),
            dbTagInfo,
        )

        logger.lifecycle(
            "Generating rd5 files with planet=${planetFile.absolutePath}, workDir=${workDir.absolutePath}, outputDir=${outputDir.absolutePath}",
        )
        if (srtmDir == null) {
            logger.lifecycle(
                "No SRTM directory configured. This matches upstream BRouter behavior: rd5 generation will succeed without elevation data.",
            )
        }
    }
}
