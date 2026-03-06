package dev.skynomads.beerouter.benchmark

import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal actual fun ensureSegmentFile(segmentFile: Path) {
    if (SystemFileSystem.exists(segmentFile)) {
        return
    }

    val url = URL("https://brouter.de/brouter/segments4/E5_N45.rd5")
    url.openStream().use { source ->
        Files.copy(source, java.nio.file.Path.of(segmentFile.toString()), StandardCopyOption.REPLACE_EXISTING)
    }
}
