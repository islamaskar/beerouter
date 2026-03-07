package dev.skynomads.beerouter.router

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import java.io.File

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

    val url = "https://brouter.de/brouter/segments4/E5_N45.rd5"
    runBlocking {
        val bytes = HttpClient(CIO).use { it.get(url).body<ByteArray>() }
        SystemFileSystem.sink(testSegmentFile).buffered().use { it.write(bytes) }
    }
}
