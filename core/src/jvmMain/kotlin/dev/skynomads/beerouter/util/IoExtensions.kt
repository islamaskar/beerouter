package dev.skynomads.beerouter.util

import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

public fun readUtf8(path: Path): String =
    SystemFileSystem.source(path).buffered().use { source -> source.readUtf8Fully() }

private fun Source.readUtf8Fully(): String {
    val bytes = mutableListOf<Byte>()
    while (!exhausted()) {
        bytes += readByte()
    }
    return bytes.toByteArray().decodeToString()
}
