package dev.skynomads.beerouter.benchmark

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

internal actual fun ensureSegmentFile(segmentFile: Path) {
    if (SystemFileSystem.exists(segmentFile)) {
        return
    }

    val url = "https://brouter.de/brouter/segments4/E5_N45.rd5"
    runBlocking {
        val bytes = HttpClient(CIO).use { it.get(url).body<ByteArray>() }
        SystemFileSystem.sink(segmentFile).buffered().use { it.write(bytes) }
    }
}
