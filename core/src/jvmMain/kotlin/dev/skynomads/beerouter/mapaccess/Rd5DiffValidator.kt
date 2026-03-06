/**
 * Manage rd5 diff-file creation
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import java.io.File

public object Rd5DiffValidator {
    @Throws(Exception::class)
    public fun main(args: Array<String>) {
        validateDiffs(File(args[0]), File(args[1]))
    }

    /**
     * Validate diffs for all DF5 files
     */
    @Throws(Exception::class)
    public fun validateDiffs(oldDir: File?, newDir: File) {
        val oldDiffDir = File(oldDir, "diff")
        val newDiffDir = File(newDir, "diff")

        val filesNew = newDir.listFiles()

        for (fn in filesNew!!) {
            val name = fn.getName()
            if (!name.endsWith(".rd5")) {
                continue
            }
            if (fn.length() < 1024 * 1024) {
                continue  // expecting no diff for small files
            }
            val basename = name.take(name.length - 4)
            val fo = File(oldDir, name)
            if (!fo.isFile()) {
                continue
            }

            // calculate MD5 of old file
            val md5 = Rd5DiffManager.getMD5(fo)

            val md5New = Rd5DiffManager.getMD5(fn)

            println("name=$name md5=$md5")

            val specificNewDiffs = File(newDiffDir, basename)

            val diffFileName = "$md5.df5"
            val diffFile = File(specificNewDiffs, diffFileName)

            val fcmp = File(oldDir, name + "_tmp")

            // merge old file and diff
            Rd5DiffTool.recoverFromDelta(fo, diffFile, fcmp, Rd5DiffTool())
            val md5Cmp = Rd5DiffManager.getMD5(fcmp)

            if (md5Cmp != md5New) {
                throw RuntimeException("**************** md5 mismatch!! *****************")
            }
        }
    }
}
