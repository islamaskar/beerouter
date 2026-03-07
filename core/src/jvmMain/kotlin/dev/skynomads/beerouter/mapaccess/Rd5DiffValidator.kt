/**
 * Manage rd5 diff-file creation
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

public object Rd5DiffValidator {
    public fun main(args: Array<String>) {
        validateDiffs(Path(args[0]), Path(args[1]))
    }

    /**
     * Validate diffs for all DF5 files
     */
    public fun validateDiffs(oldDir: Path, newDir: Path) {
        val newDiffDir = resolve(newDir, "diff")

        val filesNew = runCatching { SystemFileSystem.list(newDir) }.getOrElse { emptyList() }

        for (fn in filesNew) {
            val name = fileName(fn)
            if (!name.endsWith(".rd5")) continue
            if ((SystemFileSystem.metadataOrNull(fn)?.size ?: 0L) < 1024 * 1024) continue // expecting no diff for small files

            val basename = name.dropLast(4)
            val fo = resolve(oldDir, name)
            if (SystemFileSystem.metadataOrNull(fo)?.isRegularFile != true) continue

            val md5 = Rd5DiffManager.getMD5(fo)
            val md5New = Rd5DiffManager.getMD5(fn)

            println("name=$name md5=$md5")

            val specificNewDiffs = resolve(newDiffDir, basename)
            val diffFile = resolve(specificNewDiffs, "$md5.df5")
            val fcmp = resolve(oldDir, "${name}_tmp")

            Rd5DiffTool.recoverFromDelta(fo, diffFile, fcmp, Rd5DiffTool())
            val md5Cmp = Rd5DiffManager.getMD5(fcmp)

            if (md5Cmp != md5New) {
                throw RuntimeException("**************** md5 mismatch!! *****************")
            }
        }
    }

    private fun resolve(base: Path, child: String): Path = Path(base, child)

    private fun fileName(path: Path): String = path.toString().substringAfterLast('/').substringAfterLast('\\')
}
