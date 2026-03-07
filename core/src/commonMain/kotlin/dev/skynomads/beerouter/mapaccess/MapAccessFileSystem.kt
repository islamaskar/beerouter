package dev.skynomads.beerouter.mapaccess

import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

public interface RandomAccessReader {
    public fun seek(position: Long)
    public fun readFully(buffer: ByteArray, offset: Int, length: Int)
    public fun length(): Long
    public fun close()
}

public interface MapAccessFileSystem {
    public fun exists(fileName: String): Boolean
    public fun open(fileName: String): RandomAccessReader
}

public class DefaultMapAccessFileSystem(
    private val segmentDir: Path,
    private val fileSystem: FileSystem = SystemFileSystem,
) : MapAccessFileSystem {
    private fun resolve(fileName: String): Path = Path(segmentDir, fileName)

    override fun exists(fileName: String): Boolean = fileSystem.exists(resolve(fileName))

    override fun open(fileName: String): RandomAccessReader {
        val path = resolve(fileName)
        return if (fileSystem === SystemFileSystem) {
            openRandomAccess(path)
        } else {
            ByteArrayRandomAccessReader(fileSystem.readBytes(path))
        }
    }
}

internal expect fun openRandomAccess(path: Path): RandomAccessReader

private class ByteArrayRandomAccessReader(private val bytes: ByteArray) : RandomAccessReader {
    private var position: Int = 0

    override fun seek(position: Long) {
        require(position in 0..bytes.size.toLong()) { "seek out of bounds: $position" }
        this.position = position.toInt()
    }

    override fun readFully(buffer: ByteArray, offset: Int, length: Int) {
        val end = position + length
        require(end <= bytes.size) { "unexpected EOF reading $length bytes at $position" }
        bytes.copyInto(buffer, offset, position, end)
        position = end
    }

    override fun length(): Long = bytes.size.toLong()

    override fun close() = Unit
}

private fun FileSystem.readBytes(path: Path): ByteArray =
    source(path).buffered().use { source ->
        val result = ByteArrayList()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = source.readAtMostTo(buffer)
            if (read <= 0) {
                return@use result.toByteArray()
            }
            result.add(buffer, read)
        }
        result.toByteArray()
    }

private class ByteArrayList {
    private var bytes: ByteArray = ByteArray(8 * 1024)
    private var size: Int = 0

    fun add(chunk: ByteArray, length: Int) {
        ensureCapacity(size + length)
        chunk.copyInto(bytes, size, 0, length)
        size += length
    }

    fun toByteArray(): ByteArray = bytes.copyOf(size)

    private fun ensureCapacity(requiredSize: Int) {
        if (requiredSize <= bytes.size) {
            return
        }
        var newSize = bytes.size
        while (newSize < requiredSize) {
            newSize *= 2
        }
        bytes = bytes.copyOf(newSize)
    }
}
