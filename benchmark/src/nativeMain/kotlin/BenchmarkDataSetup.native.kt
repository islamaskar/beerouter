package dev.skynomads.beerouter.benchmark

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
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
        val bytes = HttpClient(Curl).use { it.get(url).body<ByteArray>() }
        SystemFileSystem.sink(segmentFile).buffered().use { it.write(bytes) }
    }
}
