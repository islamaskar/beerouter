/**
 * Manage rd5 diff-file creation
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.DigestInputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

public object Rd5DiffManager {
    @Throws(Exception::class)
    public fun main(args: Array<String>) {
        calcDiffs(File(args[0]), File(args[1]))
    }

    /**
     * Compute diffs for all RD5 files
     */
    @Throws(Exception::class)
    public fun calcDiffs(oldDir: File?, newDir: File) {
        val oldDiffDir = File(oldDir, "diff")
        val newDiffDir = File(newDir, "diff")

        val filesNew = newDir.listFiles()

        for (fn in filesNew!!) {
            val name = fn.getName()
            if (!name.endsWith(".rd5")) {
                continue
            }
            if (fn.length() < 1024 * 1024) {
                continue  // exclude very small files from diffing
            }
            val basename = name.take(name.length - 4)
            val fo = File(oldDir, name)
            if (!fo.isFile()) {
                continue
            }

            // calculate MD5 of old file
            val md5 = getMD5(fo)

            val md5New = getMD5(fn)

            println("name=$name md5=$md5")

            val specificNewDiffs = File(newDiffDir, basename)
            specificNewDiffs.mkdirs()

            val diffFileName = "$md5.df5"
            val diffFile = File(specificNewDiffs, diffFileName)

            val dummyDiffFileName = "$md5New.df5"
            val dummyDiffFile = File(specificNewDiffs, dummyDiffFileName)
            dummyDiffFile.createNewFile()

            // calc the new diff
            Rd5DiffTool.diff2files(fo, fn, diffFile)

            // ... and add that to old diff files
            val specificOldDiffs = File(oldDiffDir, basename)
            if (specificOldDiffs.isDirectory()) {
                val oldDiffs = specificOldDiffs.listFiles()
                for (od in oldDiffs!!) {
                    if (!od.getName().endsWith(".df5")) {
                        continue
                    }
                    if (System.currentTimeMillis() - od.lastModified() > 9 * 86400000L) {
                        continue  // limit diff history to 9 days
                    }

                    val updatedDiff = File(specificNewDiffs, od.getName())
                    if (!updatedDiff.exists()) {
                        Rd5DiffTool.addDeltas(od, diffFile, updatedDiff)
                        updatedDiff.setLastModified(od.lastModified())
                    }
                }
            }
        }
    }

    @Throws(IOException::class)
    public fun getMD5(f: File): String {
        try {
            val md = MessageDigest.getInstance("MD5")
            val bis = BufferedInputStream(FileInputStream(f))
            val dis = DigestInputStream(bis, md)
            val buf = ByteArray(8192)
            while (true) {
                val len = dis.read(buf)
                if (len <= 0) {
                    break
                }
            }
            dis.close()
            val bytes = md.digest()

            val sb = StringBuilder()
            for (j in bytes.indices) {
                val v = bytes[j].toInt() and 0xff
                sb.append(hexChar(v ushr 4)).append(hexChar(v and 0xf))
            }
            return sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            throw IOException("MD5 algorithm not available", e)
        }
    }

    private fun hexChar(v: Int): Char {
        return (if (v > 9) 'a'.code + (v - 10) else '0'.code + v).toChar()
    }
}
