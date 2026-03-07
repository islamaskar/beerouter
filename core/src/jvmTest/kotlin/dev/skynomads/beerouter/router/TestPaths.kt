package dev.skynomads.beerouter.router

import java.io.File
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

