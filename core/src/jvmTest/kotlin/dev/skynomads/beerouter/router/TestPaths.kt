package dev.skynomads.beerouter.router

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal val testSegmentFile: Path = Path(System.getProperty("java.io.tmpdir"), "E5_N45.rd5")
internal val generatedTestSegmentDir: Path =
    Path(requireNotNull(System.getProperty("beerouter.generatedSegmentDir")) {
        "missing system property beerouter.generatedSegmentDir"
    })

internal fun profilePath(profileName: String): Path {
    val candidates = listOf(
        Path(System.getProperty("user.dir"), "misc", "profiles2", profileName),
        Path(System.getProperty("user.dir"), "..", "misc", "profiles2", profileName)
    )
    return candidates.firstOrNull(SystemFileSystem::exists)
        ?: error("missing profile file for $profileName")
}

internal fun Path.toJavaFile(): File = File(toString())

internal fun ensureTestSegmentFile() {
    if (SystemFileSystem.exists(testSegmentFile)) return

    val url = URI("https://segments.skynomads.dev/v10/E5_N45.rd5").toURL()
    url.openStream().use { source ->
        SystemFileSystem.sink(testSegmentFile).buffered().use { sink ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
            }
        }
    }
}

internal fun ensureGeneratedTestSegmentDir() {
    require(SystemFileSystem.exists(generatedTestSegmentDir)) {
        "missing generated test segment directory: $generatedTestSegmentDir"
    }
}

internal fun lookupPathForSegments(segmentDir: Path): Path {
    val segmentLookup = Path(segmentDir, "lookups.dat")
    return if (SystemFileSystem.exists(segmentLookup)) {
        segmentLookup
    } else {
        Path(profilePath("trekking.brf").parent ?: error("profile parent missing"), "lookups.dat")
    }
}

internal fun profilePathForSegments(profileName: String, segmentDir: Path): Path {
    val profile = profilePath(profileName)
    val segmentLookup = Path(segmentDir, "lookups.dat")
    if (!SystemFileSystem.exists(segmentLookup)) {
        return profile
    }

    val cacheDir = Path(
        System.getProperty("java.io.tmpdir"),
        "beerouter-routing-test-profiles",
        segmentDir.toString().hashCode().toUInt().toString(),
    )
    val cachedProfile = Path(cacheDir, profileName)
    if (SystemFileSystem.exists(cachedProfile)) {
        return cachedProfile
    }

    Files.createDirectories(File(cacheDir.toString()).toPath())
    val sourceDir = File(requireNotNull(profile.parent).toString())
    sourceDir.listFiles()?.forEach { source ->
        if (source.isFile) {
            Files.copy(
                source.toPath(),
                File(cacheDir.toString(), source.name).toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
    Files.copy(
        File(segmentLookup.toString()).toPath(),
        File(cacheDir.toString(), "lookups.dat").toPath(),
        StandardCopyOption.REPLACE_EXISTING,
    )
    return cachedProfile
}
