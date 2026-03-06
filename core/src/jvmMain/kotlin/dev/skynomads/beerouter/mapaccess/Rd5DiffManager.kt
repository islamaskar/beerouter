/**
 * Manage rd5 diff-file creation
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import java.security.MessageDigest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

public object Rd5DiffManager {
    public fun main(args: Array<String>) {
        calcDiffs(Path(args[0]), Path(args[1]))
    }

    /**
     * Compute diffs for all RD5 files
     */
    public fun calcDiffs(oldDir: Path, newDir: Path) {
        val oldDiffDir = resolve(oldDir, "diff")
        val newDiffDir = resolve(newDir, "diff")

        val filesNew = runCatching { SystemFileSystem.list(newDir) }.getOrElse { emptyList() }

        for (fn in filesNew) {
            val name = fileName(fn)
            if (!name.endsWith(".rd5")) continue
            if ((SystemFileSystem.metadataOrNull(fn)?.size ?: 0L) < 1024 * 1024) continue // exclude very small files from diffing

            val basename = name.dropLast(4)
            val fo = resolve(oldDir, name)
            if (SystemFileSystem.metadataOrNull(fo)?.isRegularFile != true) continue

            val md5 = getMD5(fo)
            val md5New = getMD5(fn)

            println("name=$name md5=$md5")

            val specificNewDiffs = resolve(newDiffDir, basename)
            SystemFileSystem.createDirectories(specificNewDiffs)

            val diffFile = resolve(specificNewDiffs, "$md5.df5")
            SystemFileSystem.sink(resolve(specificNewDiffs, "$md5New.df5")).buffered().use { }

            Rd5DiffTool.diff2files(fo, fn, diffFile)

            val specificOldDiffs = resolve(oldDiffDir, basename)
            if (SystemFileSystem.metadataOrNull(specificOldDiffs)?.isDirectory == true) {
                val oldDiffs = runCatching { SystemFileSystem.list(specificOldDiffs) }.getOrElse { emptyList() }
                for (od in oldDiffs) {
                    if (!fileName(od).endsWith(".df5")) continue

                    val updatedDiff = resolve(specificNewDiffs, fileName(od))
                    if (!SystemFileSystem.exists(updatedDiff)) {
                        Rd5DiffTool.addDeltas(od, diffFile, updatedDiff)
                    }
                }
            }
        }
    }

    public fun getMD5(path: Path): String {
        val md = MessageDigest.getInstance("MD5")
        SystemFileSystem.source(path).buffered().use { source ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = source.readAtMostTo(buffer)
                if (read <= 0) {
                    break
                }
                md.update(buffer, 0, read)
            }
        }
        val bytes = md.digest()
        return buildString {
            for (b in bytes) {
                val v = b.toInt() and 0xff
                append(hexChar(v ushr 4))
                append(hexChar(v and 0xf))
            }
        }
    }

    private fun hexChar(v: Int): Char {
        return (if (v > 9) 'a'.code + (v - 10) else '0'.code + v).toChar()
    }

    private fun resolve(base: Path, child: String): Path = Path("${base.toString()}/$child")

    private fun fileName(path: Path): String = path.toString().substringAfterLast('/').substringAfterLast('\\')
}
