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
    public fun resolve(segmentDir: Path, fileName: String): Path?
    public fun exists(path: Path): Boolean
    public fun openRandomAccess(path: Path): RandomAccessReader
}

public object DefaultMapAccessFileSystem : MapAccessFileSystem {
    override fun resolve(segmentDir: Path, fileName: String): Path = Path("${segmentDir.toString()}/$fileName")

    override fun exists(path: Path): Boolean = SystemFileSystem.exists(path)

    override fun openRandomAccess(path: Path): RandomAccessReader = JvmRandomAccessReader(path)
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
