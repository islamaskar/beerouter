package dev.skynomads.beerouter.mapaccess

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.io.EOFException
import kotlinx.io.files.Path
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

internal actual fun openRandomAccess(path: Path): RandomAccessReader = PosixRandomAccessReader(path)

@OptIn(ExperimentalForeignApi::class)
private class PosixRandomAccessReader(private val path: Path) : RandomAccessReader {
    private val file = fopen(path.toString(), "rb")
        ?: throw IllegalStateException("failed to open $path")

    override fun seek(position: Long) {
        fseek(file, position, SEEK_SET)
    }

    override fun readFully(buffer: ByteArray, offset: Int, length: Int) {
        buffer.usePinned { pinned ->
            var remaining = length
            var off = offset
            while (remaining > 0) {
                val n = fread(pinned.addressOf(off), 1u, remaining.toULong(), file).toInt()
                if (n <= 0) throw EOFException("unexpected EOF reading $path")
                off += n
                remaining -= n
            }
        }
    }

    override fun length(): Long {
        val saved = ftell(file)
        fseek(file, 0, SEEK_END)
        val len = ftell(file)
        fseek(file, saved, SEEK_SET)
        return len
    }

    override fun close() {
        fclose(file)
    }
}
