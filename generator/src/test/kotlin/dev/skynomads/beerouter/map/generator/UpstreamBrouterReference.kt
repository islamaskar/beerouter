package dev.skynomads.beerouter.map.generator

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.test.assertTrue

/**
 * Uses the upstream BRouter fat jar as a reference implementation for rd5 generation.
 *
 * The pinned commit and command sequence match the upstream documentation and code:
 * - docs/developers/build_segments.md
 * - misc/scripts/mapcreation/process_pbf_planet.sh
 * - brouter-map-creator/src/test/java/btools/mapcreator/GeneratorTest.java
 */
internal object UpstreamBrouterReference {
    private const val upstreamCommit: String = "46b6657e1f8ce8ec5858c1c6257fcea25b84abde"
    private val cacheRoot: Path =
        Path.of(System.getProperty("java.io.tmpdir"), "beerouter-brouter-reference", upstreamCommit)

    fun generateSegments(
        baseDir: File,
        mapFile: File,
        srtmDir: File,
        profileDir: File,
    ): File {
        val fatJar = ensureFatJar()
        baseDir.mkdirs()

        val nodes = File(baseDir, "nodetiles").apply { mkdir() }
        val ways = File(baseDir, "waytiles").apply { mkdir() }
        val nodes55 = File(baseDir, "nodes55").apply { mkdir() }
        val ways55 = File(baseDir, "waytiles55").apply { mkdir() }
        val lookupFile = File(profileDir, "lookups.dat")
        val relFile = File(baseDir, "cycleways.dat")
        val resFile = File(baseDir, "restrictions.dat")
        val profileAll = File(profileDir, "all.brf")
        val profileReport = File(profileDir, "trekking.brf")
        val profileCheck = File(profileDir, "softaccess.brf")
        val borderFile = File(baseDir, "bordernids.dat")
        val unodes55 = File(baseDir, "unodes55").apply { mkdir() }
        val bordernodes = File(baseDir, "bordernodes.dat")
        val segments = File(baseDir, "segments").apply { mkdir() }

        runJava(
            fatJar,
            "btools.mapcreator.OsmFastCutter",
            listOf(
                lookupFile.absolutePath,
                nodes.absolutePath,
                ways.absolutePath,
                nodes55.absolutePath,
                ways55.absolutePath,
                borderFile.absolutePath,
                relFile.absolutePath,
                resFile.absolutePath,
                profileAll.absolutePath,
                profileReport.absolutePath,
                profileCheck.absolutePath,
                mapFile.absolutePath,
            ),
        )

        runJava(
            fatJar,
            "btools.mapcreator.PosUnifier",
            listOf(
                nodes55.absolutePath,
                unodes55.absolutePath,
                borderFile.absolutePath,
                bordernodes.absolutePath,
                srtmDir.absolutePath,
            ),
        )

        runJava(
            fatJar,
            "btools.mapcreator.WayLinker",
            listOf(
                unodes55.absolutePath,
                ways55.absolutePath,
                bordernodes.absolutePath,
                resFile.absolutePath,
                lookupFile.absolutePath,
                profileAll.absolutePath,
                segments.absolutePath,
                "rd5",
            ),
        )

        assertTrue(segments.listFiles()?.isNotEmpty() == true, "brouter reference segments directory should not be empty")
        return segments
    }

    @Synchronized
    private fun ensureFatJar(): File {
        cacheRoot.createDirectories()
        val sourceDir = cacheRoot.resolve("repo")
        if (!Files.exists(sourceDir)) {
            downloadAndExtractSource(sourceDir)
        }

        val fatJar = locateFatJar(sourceDir)
        if (fatJar != null) {
            return fatJar.toFile()
        }

        buildFatJar(sourceDir)
        return locateFatJar(sourceDir)?.toFile()
            ?: error("upstream BRouter fat jar missing after build in $sourceDir")
    }

    private fun downloadAndExtractSource(targetDir: Path) {
        val zipFile = cacheRoot.resolve("brouter-$upstreamCommit.zip")
        if (!Files.exists(zipFile)) {
            URI("https://codeload.github.com/abrensch/brouter/zip/$upstreamCommit").toURL().openStream().use { input ->
                Files.copy(input, zipFile)
            }
        }

        val extractRoot = cacheRoot.resolve("extract")
        extractRoot.toFile().deleteRecursively()
        extractRoot.createDirectories()

        ZipInputStream(Files.newInputStream(zipFile)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val output = extractRoot.resolve(entry.name).normalize()
                require(output.startsWith(extractRoot)) { "zip entry escapes extraction root: ${entry.name}" }
                if (entry.isDirectory) {
                    Files.createDirectories(output)
                } else {
                    Files.createDirectories(output.parent)
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
            }
        }

        val extractedDir = extractRoot.toFile()
            .listFiles(File::isDirectory)
            ?.singleOrNull()
            ?: error("expected a single extracted BRouter source directory under $extractRoot")
        Files.move(extractedDir.toPath(), targetDir, StandardCopyOption.REPLACE_EXISTING)
        extractRoot.toFile().deleteRecursively()
    }

    private fun buildFatJar(sourceDir: Path) {
        val repoDir = sourceDir.toFile()
        val gradleWrapper = if (isWindows()) File(repoDir, "gradlew.bat") else File(repoDir, "gradlew")
        if (!isWindows()) {
            gradleWrapper.setExecutable(true)
        }

        val command = if (isWindows()) {
            listOf("cmd", "/c", gradleWrapper.absolutePath, "--no-daemon", ":brouter-server:fatJar")
        } else {
            listOf("bash", gradleWrapper.absolutePath, "--no-daemon", ":brouter-server:fatJar")
        }
        runCommand(command, repoDir, timeoutSeconds = 15 * 60)
    }

    private fun locateFatJar(sourceDir: Path): Path? =
        sourceDir.resolve("brouter-server/build/libs")
            .toFile()
            .listFiles()
            ?.filter { it.isFile && it.name.startsWith("brouter-") && it.name.endsWith("-all.jar") }
            ?.maxByOrNull(File::lastModified)
            ?.toPath()

    private fun runJava(
        fatJar: File,
        mainClass: String,
        args: List<String>,
    ) {
        val javaExecutable = File(
            File(System.getProperty("java.home"), "bin"),
            if (isWindows()) "java.exe" else "java",
        )
        val command = buildList {
            add(javaExecutable.absolutePath)
            add("-DavoidMapPolling=true")
            add("-cp")
            add(fatJar.absolutePath)
            add(mainClass)
            addAll(args)
        }
        runCommand(command, fatJar.parentFile, timeoutSeconds = 10 * 60)
    }

    private fun runCommand(
        command: List<String>,
        workingDir: File,
        timeoutSeconds: Long,
    ): String {
        val process = ProcessBuilder(command)
            .directory(workingDir)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = thread(start = true, name = "process-output-reader") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach {
                    output.appendLine(it)
                }
            }
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            reader.join()
            error("command timed out after ${timeoutSeconds}s: ${command.joinToString(" ")}\n$output")
        }
        reader.join()
        check(process.exitValue() == 0) {
            "command failed (${process.exitValue()}): ${command.joinToString(" ")}\n$output"
        }
        return output.toString()
    }

    private fun isWindows(): Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
}
