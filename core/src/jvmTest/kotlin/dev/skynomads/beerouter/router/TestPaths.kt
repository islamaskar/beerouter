package dev.skynomads.beerouter.router

import java.io.File
import java.net.URI
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal val testSegmentFile: Path = Path(System.getProperty("java.io.tmpdir"), "E5_N45.rd5")

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

    val url = URI("https://brouter.de/brouter/segments4/E5_N45.rd5").toURL()
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
