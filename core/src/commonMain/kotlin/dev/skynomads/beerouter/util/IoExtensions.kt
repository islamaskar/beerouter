package dev.skynomads.beerouter.util

import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

public fun readUtf8(path: Path): String =
    SystemFileSystem.source(path).buffered().use { source -> source.readUtf8Fully() }

public fun readUtf8(source: Source): String = source.readUtf8Fully()

private fun Source.readUtf8Fully(): String {
    var result = ByteArray(IO_BUFFER_SIZE)
    val scratch = ByteArray(IO_BUFFER_SIZE)
    var size = 0

    while (true) {
        val read = readAtMostTo(scratch)
        if (read <= 0) {
            break
        }

        val required = size + read
        if (required > result.size) {
            var newCapacity = result.size * 2
            while (newCapacity < required) {
                newCapacity *= 2
            }
            result = result.copyOf(newCapacity)
        }

        scratch.copyInto(result, destinationOffset = size, endIndex = read)
        size = required
    }

    return result.decodeToString(startIndex = 0, endIndex = size)
}

private const val IO_BUFFER_SIZE = 8 * 1024
