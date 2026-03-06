/**
 * Manage rd5 diff-file creation
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import java.io.File
import kotlinx.io.files.Path

public object Rd5DiffValidator {
    public fun main(args: Array<String>) {
        validateDiffs(Path(args[0]), Path(args[1]))
    }

    /**
     * Validate diffs for all DF5 files
     */
    public fun validateDiffs(oldDir: Path, newDir: Path) {
        validateDiffs(File(oldDir.toString()), File(newDir.toString()))
    }

    public fun validateDiffs(oldDir: File, newDir: File) {
        val newDiffDir = File(newDir, "diff")

        val filesNew = newDir.listFiles() ?: return

        for (fn in filesNew) {
            val name = fn.name
            if (!name.endsWith(".rd5")) continue
            if (fn.length() < 1024 * 1024) continue  // expecting no diff for small files

            val basename = name.dropLast(4)
            val fo = File(oldDir, name)
            if (!fo.isFile) continue

            val md5 = Rd5DiffManager.getMD5(fo)
            val md5New = Rd5DiffManager.getMD5(fn)

            println("name=$name md5=$md5")

            val specificNewDiffs = File(newDiffDir, basename)
            val diffFile = File(specificNewDiffs, "$md5.df5")
            val fcmp = File(oldDir, "${name}_tmp")

            Rd5DiffTool.recoverFromDelta(fo, diffFile, fcmp, Rd5DiffTool())
            val md5Cmp = Rd5DiffManager.getMD5(fcmp)

            if (md5Cmp != md5New) {
                throw RuntimeException("**************** md5 mismatch!! *****************")
            }
        }
    }
}
