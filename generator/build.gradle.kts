import org.gradle.api.tasks.testing.logging.TestExceptionFormat
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
    explicitApi()
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
val routingTestExtract = "europe/germany/baden-wuerttemberg/tuebingen-regbez-latest"
val routingTestExtractPath = normalizedExtractPath(routingTestExtract)
val routingTestExtractSlug = geofabrikExtractSlug(routingTestExtractPath)

fun resolveConfiguredFile(path: String): File {
    val candidate = File(path)
    return if (candidate.isAbsolute) candidate else rootProject.file(path)
}

fun configuredFileProperty(name: String, defaultPath: String): File =
    resolveConfiguredFile(providers.gradleProperty(name).orNull ?: defaultPath)

fun configuredStringProperty(name: String): String? = providers.gradleProperty(name).orNull

fun normalizedExtractPath(rawExtract: String): String =
    rawExtract
        .trim()
        .removePrefix("/")
        .removeSuffix("/")
        .removeSuffix(".osm.pbf")
        .removeSuffix(".html")
        .removeSuffix("-latest")

fun geofabrikExtractPathProperty(name: String): String {
    val rawValue = configuredStringProperty(name)
        ?: error("Missing required Gradle property -P$name=<geofabrik-path>, for example -P$name=europe/germany")
    val extractPath = normalizedExtractPath(rawValue)
    require(extractPath.isNotEmpty() && !extractPath.contains("..")) {
        "Invalid Geofabrik extract path for $name: $rawValue"
    }
    return extractPath
}

fun geofabrikExtractSlug(extractPath: String): String = extractPath.substringAfterLast('/')

fun httpDownloadIfNeeded(targetFile: File, sourceUri: URI) {
    targetFile.parentFile.mkdirs()
    val connection = sourceUri.toURL().openConnection()
    if (targetFile.exists()) {
        connection.ifModifiedSince = targetFile.lastModified()
    }
    if (connection is HttpURLConnection) {
        connection.instanceFollowRedirects = true
    }
    connection.connect()

    if (connection is HttpURLConnection && connection.responseCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
        logger.lifecycle("Source file is current: ${targetFile.absolutePath}")
        return
    }

    val tempFile = File(targetFile.parentFile, "${targetFile.name}.download")
    connection.getInputStream().use { input ->
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
    }
    Files.move(tempFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
    if (connection.lastModified > 0L) {
        targetFile.setLastModified(connection.lastModified)
    }
    logger.lifecycle("Downloaded ${sourceUri} to ${targetFile.absolutePath}")
}

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
            val executable = File(
                dir,
                if (name.endsWith(extension, ignoreCase = true)) name else name + extension
            )
            if (executable.isFile && executable.canExecute()) {
                return executable
            }
        }
    }
    return null
}

tasks.register("downloadPlanetOsmPbf") {
    group = "generator"
    description =
        "Download the latest OSM planet PBF, or update an existing copy with osmupdate when available."

    val defaultPlanetFile = layout.buildDirectory.file("planet/planet-latest.osm.pbf")
    outputs.file(defaultPlanetFile)

    doLast {
        val planetFile = configuredFileProperty(
            "generator.planetFile",
            defaultPlanetFile.get().asFile.absolutePath,
        )
        val planetUri = URI(
            configuredStringProperty("generator.planetUrl")
                ?: "https://planet.openstreetmap.org/pbf/planet-latest.osm.pbf",
        )
        planetFile.parentFile.mkdirs()

        val osmUpdate = findExecutable("osmupdate")
        if (planetFile.exists() && osmUpdate != null) {
            val backupFile = File(planetFile.parentFile, "${planetFile.name}.old")
            Files.move(
                planetFile.toPath(),
                backupFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
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
                Files.move(
                    backupFile.toPath(),
                    planetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                throw e
            }
            return@doLast
        }

        httpDownloadIfNeeded(planetFile, planetUri)
    }
}

tasks.register("downloadCountryOsmPbf") {
    group = "generator"
    description = "Download a Geofabrik country extract as <extract>-latest.osm.pbf."

    doLast {
        val extractPath = geofabrikExtractPathProperty("generator.country")
        val extractSlug = geofabrikExtractSlug(extractPath)
        val countryFile = configuredFileProperty(
            "generator.countryFile",
            layout.buildDirectory.file("country/$extractSlug/${extractSlug}-latest.osm.pbf")
                .get().asFile.absolutePath,
        )
        val countryUri = URI(
            configuredStringProperty("generator.countryUrl")
                ?: "https://download.geofabrik.de/$extractPath-latest.osm.pbf",
        )
        httpDownloadIfNeeded(countryFile, countryUri)
    }
}

tasks.register("downloadRoutingTestCountryOsmPbf") {
    group = "generator"
    description = "Download the fixed Geofabrik extract used by routing parity tests."

    val defaultCountryFile = rootProject.layout.buildDirectory.file(
        "routing-test/$routingTestExtractSlug/${routingTestExtractSlug}-latest.osm.pbf",
    )
    outputs.file(defaultCountryFile)

    doLast {
        val countryFile = resolveConfiguredFile(defaultCountryFile.get().asFile.absolutePath)
        val countryUri = URI("https://download.geofabrik.de/$routingTestExtractPath-latest.osm.pbf")
        httpDownloadIfNeeded(countryFile, countryUri)
    }
}

tasks.register<JavaExec>("generatePlanetRd5") {
    group = "generator"
    description =
        "Generate BRouter-compatible rd5 files for a planet PBF using the Beerouter Kotlin generator pipeline."

    dependsOn("classes", "downloadPlanetOsmPbf")

    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("dev.skynomads.beerouter.map.generator.PlanetRd5Generator")

    val defaultPlanetFile = layout.buildDirectory.file("planet/planet-latest.osm.pbf")
    val defaultWorkDir = layout.buildDirectory.dir("planet/work")
    val defaultOutputDir = layout.buildDirectory.dir("planet/segments4")

    doFirst {
        val planetFile = configuredFileProperty(
            "generator.planetFile",
            defaultPlanetFile.get().asFile.absolutePath,
        )
        val workDir = configuredFileProperty(
            "generator.workDir",
            defaultWorkDir.get().asFile.absolutePath,
        )
        val outputDir = configuredFileProperty(
            "generator.outputDir",
            defaultOutputDir.get().asFile.absolutePath,
        )
        val profileDir = configuredFileProperty(
            "generator.profileDir",
            rootProject.file("misc/profiles2").absolutePath,
        )
        val srtmDir = configuredStringProperty("generator.srtmDir")?.let(::resolveConfiguredFile)
        val srtmFallbackDir =
            configuredStringProperty("generator.srtmFallbackDir")?.let(::resolveConfiguredFile)
        val dbTagInfo = configuredStringProperty("generator.dbTagInfo").orEmpty()
        val poiConfig =
            configuredStringProperty("generator.poiConfig")?.let(::resolveConfiguredFile)

        args = listOf(
            planetFile.absolutePath,
            workDir.absolutePath,
            outputDir.absolutePath,
            profileDir.absolutePath,
            srtmDir?.absolutePath.orEmpty(),
            srtmFallbackDir?.absolutePath.orEmpty(),
            dbTagInfo,
            poiConfig?.absolutePath.orEmpty(),
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

tasks.register<JavaExec>("generateCountryRd5") {
    group = "generator"
    description =
        "Download a Geofabrik country extract and generate rd5 files for that single extract."

    dependsOn("classes", "downloadCountryOsmPbf")

    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("dev.skynomads.beerouter.map.generator.PlanetRd5Generator")

    doFirst {
        val extractPath = geofabrikExtractPathProperty("generator.country")
        val extractSlug = geofabrikExtractSlug(extractPath)
        val countryFile = configuredFileProperty(
            "generator.countryFile",
            layout.buildDirectory.file("country/$extractSlug/${extractSlug}-latest.osm.pbf")
                .get().asFile.absolutePath,
        )
        val workDir = configuredFileProperty(
            "generator.workDir",
            layout.buildDirectory.dir("country/$extractSlug/work").get().asFile.absolutePath,
        )
        val outputDir = configuredFileProperty(
            "generator.outputDir",
            layout.buildDirectory.dir("country/$extractSlug/segments4").get().asFile.absolutePath,
        )
        val profileDir = configuredFileProperty(
            "generator.profileDir",
            rootProject.file("misc/profiles2").absolutePath,
        )
        val srtmDir = configuredStringProperty("generator.srtmDir")?.let(::resolveConfiguredFile)
        val srtmFallbackDir =
            configuredStringProperty("generator.srtmFallbackDir")?.let(::resolveConfiguredFile)
        val dbTagInfo = configuredStringProperty("generator.dbTagInfo").orEmpty()
        val poiConfig =
            configuredStringProperty("generator.poiConfig")?.let(::resolveConfiguredFile)

        args = listOf(
            countryFile.absolutePath,
            workDir.absolutePath,
            outputDir.absolutePath,
            profileDir.absolutePath,
            srtmDir?.absolutePath.orEmpty(),
            srtmFallbackDir?.absolutePath.orEmpty(),
            dbTagInfo,
            poiConfig?.absolutePath.orEmpty(),
        )

        logger.lifecycle(
            "Generating country rd5 with extract=$extractPath, source=${countryFile.absolutePath}, workDir=${workDir.absolutePath}, outputDir=${outputDir.absolutePath}",
        )
        if (srtmDir == null) {
            logger.lifecycle(
                "No SRTM directory configured. This matches upstream BRouter behavior: rd5 generation will succeed without elevation data.",
            )
        }
    }
}

tasks.register<JavaExec>("generateRoutingTestRd5WithPois") {
    group = "generator"
    description = "Generate the fixed Beerouter rd5 extract used by routing parity tests, with POIs."

    dependsOn("classes", "downloadRoutingTestCountryOsmPbf")

    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("dev.skynomads.beerouter.map.generator.PlanetRd5Generator")

    val defaultCountryFile = rootProject.layout.buildDirectory.file(
        "routing-test/$routingTestExtractSlug/${routingTestExtractSlug}-latest.osm.pbf",
    )
    val defaultWorkDir =
        rootProject.layout.buildDirectory.dir("routing-test/$routingTestExtractSlug/work")
    val defaultOutputDir =
        rootProject.layout.buildDirectory.dir("routing-test/$routingTestExtractSlug/segments4")
    val defaultPoiConfig = rootProject.file("pois.conf")

    inputs.file(defaultCountryFile)
    inputs.file(defaultPoiConfig)
    outputs.dir(defaultOutputDir)

    doFirst {
        val profileDir = rootProject.file("misc/profiles2")
        args = listOf(
            defaultCountryFile.get().asFile.absolutePath,
            defaultWorkDir.get().asFile.absolutePath,
            defaultOutputDir.get().asFile.absolutePath,
            profileDir.absolutePath,
            "",
            "",
            "",
            defaultPoiConfig.absolutePath,
        )
        logger.lifecycle(
            "Generating routing test rd5 from Geofabrik extract $routingTestExtractPath into ${defaultOutputDir.get().asFile.absolutePath} with POIs from ${defaultPoiConfig.absolutePath}",
        )
    }
}

tasks.register<JavaExec>("generateRoutingTestRd5NoPois") {
    group = "generator"
    description = "Generate the fixed Beerouter rd5 extract used by routing parity tests, without POIs."

    dependsOn("classes", "downloadRoutingTestCountryOsmPbf")

    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("dev.skynomads.beerouter.map.generator.PlanetRd5Generator")

    val defaultCountryFile = rootProject.layout.buildDirectory.file(
        "routing-test/$routingTestExtractSlug/${routingTestExtractSlug}-latest.osm.pbf",
    )
    val defaultWorkDir =
        rootProject.layout.buildDirectory.dir("routing-test/$routingTestExtractSlug/work-no-pois")
    val defaultOutputDir =
        rootProject.layout.buildDirectory.dir("routing-test/$routingTestExtractSlug/segments4-no-pois")

    inputs.file(defaultCountryFile)
    outputs.dir(defaultOutputDir)

    doFirst {
        val profileDir = rootProject.file("misc/profiles2")
        args = listOf(
            defaultCountryFile.get().asFile.absolutePath,
            defaultWorkDir.get().asFile.absolutePath,
            defaultOutputDir.get().asFile.absolutePath,
            profileDir.absolutePath,
            "",
            "",
            "",
            "",
        )
        logger.lifecycle(
            "Generating routing test rd5 from Geofabrik extract $routingTestExtractPath into ${defaultOutputDir.get().asFile.absolutePath} without POIs",
        )
    }
}
