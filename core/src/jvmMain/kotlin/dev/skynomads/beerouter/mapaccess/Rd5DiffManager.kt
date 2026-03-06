/**
 * Manage rd5 diff-file creation
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

public object Rd5DiffManager {
    public fun main(args: Array<String>) {
        calcDiffs(File(args[0]), File(args[1]))
    }

    /**
     * Compute diffs for all RD5 files
     */
    public fun calcDiffs(oldDir: File, newDir: File) {
        val oldDiffDir = File(oldDir, "diff")
        val newDiffDir = File(newDir, "diff")

        val filesNew = newDir.listFiles() ?: return

        for (fn in filesNew) {
            val name = fn.name
            if (!name.endsWith(".rd5")) continue
            if (fn.length() < 1024 * 1024) continue  // exclude very small files from diffing

            val basename = name.dropLast(4)
            val fo = File(oldDir, name)
            if (!fo.isFile) continue

            val md5 = getMD5(fo)
            val md5New = getMD5(fn)

            println("name=$name md5=$md5")

            val specificNewDiffs = File(newDiffDir, basename)
            specificNewDiffs.mkdirs()

            val diffFile = File(specificNewDiffs, "$md5.df5")
            File(specificNewDiffs, "$md5New.df5").createNewFile()

            Rd5DiffTool.diff2files(fo, fn, diffFile)

            val specificOldDiffs = File(oldDiffDir, basename)
            if (specificOldDiffs.isDirectory) {
                val oldDiffs = specificOldDiffs.listFiles() ?: continue
                for (od in oldDiffs) {
                    if (!od.name.endsWith(".df5")) continue
                    if (System.currentTimeMillis() - od.lastModified() > 9 * 86400000L) continue

                    val updatedDiff = File(specificNewDiffs, od.name)
                    if (!updatedDiff.exists()) {
                        Rd5DiffTool.addDeltas(od, diffFile, updatedDiff)
                        updatedDiff.setLastModified(od.lastModified())
                    }
                }
            }
        }
    }

    public fun getMD5(f: File): String {
        val md = MessageDigest.getInstance("MD5")
        f.inputStream().buffered().use { input ->
            DigestInputStream(input, md).use { digestInput ->
                val buf = ByteArray(8192)
                while (digestInput.read(buf) > 0) { /* read all */ }
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
}
