package dev.skynomads.beerouter.benchmark

import java.net.URI
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal actual fun ensureSegmentFile(segmentFile: Path) {
    if (SystemFileSystem.exists(segmentFile)) {
        return
    }

    val url = URI("https://brouter.de/brouter/segments4/E5_N45.rd5").toURL()
    url.openStream().use { source ->
        SystemFileSystem.sink(segmentFile).buffered().use { sink ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                sink.write(buffer, 0, read)
            }
        }
    }
}
