package dev.skynomads.beerouter.mapaccess

import java.io.RandomAccessFile
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

public class DefaultMapAccessFileSystem(private val segmentDir: Path) : MapAccessFileSystem {
    private fun resolve(fileName: String): Path = Path("${segmentDir.toString()}/$fileName")

    override fun exists(fileName: String): Boolean = SystemFileSystem.exists(resolve(fileName))

    override fun open(fileName: String): RandomAccessReader = JvmRandomAccessReader(resolve(fileName))
}

private class JvmRandomAccessReader(path: Path) : RandomAccessReader {
    private val delegate = RandomAccessFile(path.toString(), "r")

    override fun seek(position: Long) {
        delegate.seek(position)
    }

    override fun readFully(buffer: ByteArray, offset: Int, length: Int) {
        delegate.readFully(buffer, offset, length)
    }

    override fun length(): Long = delegate.length()

    override fun close() {
        delegate.close()
    }
}
