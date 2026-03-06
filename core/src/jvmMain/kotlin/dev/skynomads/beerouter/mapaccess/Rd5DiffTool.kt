/**
 * Calculate, add or merge rd5 delta files
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.DataBuffers
import dev.skynomads.beerouter.codec.MicroCache
import dev.skynomads.beerouter.codec.MicroCache2
import dev.skynomads.beerouter.codec.StatCoderContext
import dev.skynomads.beerouter.util.Crc32.crc
import dev.skynomads.beerouter.util.ProgressListener
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

public class Rd5DiffTool : ProgressListener {
    override fun updateProgress(task: String?, progress: Int) {
        println("$task: $progress%")
    }

    override val isCanceled: Boolean
        get() = false

    private class MCOutputStream(
        private val dos: DataOutputStream,
        private val buffer: ByteArray?
    ) {
        private var skips: Short = 0

        public fun writeMC(mc: MicroCache): Int {
            if (mc.size == 0) {
                skips++
                return 0
            }
            dos.writeShort(skips.toInt())
            skips = 0
            val len = mc.encodeMicroCache(buffer!!)
            require(len != 0) { "encoded buffer of non-empty micro-cache cannot be empty" }
            dos.writeInt(len)
            dos.write(buffer, 0, len)
            return len
        }

        public fun finish() {
            if (skips > 0) {
                dos.writeShort(skips.toInt())
                skips = 0
            }
        }
    }

    private class MCInputStream(
        private val dis: DataInputStream,
        private val dataBuffers: DataBuffers?
    ) {
        private var skips: Short = -1
        private val empty: MicroCache = MicroCache.emptyCache()

        public fun readMC(): MicroCache {
            if (skips < 0) {
                skips = dis.readShort()
            }
            var mc = empty
            if (skips.toInt() == 0) {
                val size = dis.readInt()
                val ab = ByteArray(size)
                dis.readFully(ab)
                val bc = StatCoderContext(ab)
                mc = MicroCache2(bc, dataBuffers!!, 0, 0, 32, null, null)
            }
            skips--
            return mc
        }

        public fun finish() {
            skips = -1
        }
    }

    public companion object {
        public fun main(args: Array<String>) {
            if (args.size == 2) {
                reEncode(File(args[0]), File(args[1]))
                return
            }

            if (args[1].endsWith(".df5")) {
                if (args[0].endsWith(".df5")) {
                    addDeltas(File(args[0]), File(args[1]), File(args[2]))
                } else {
                    recoverFromDelta(
                        File(args[0]),
                        File(args[1]),
                        File(args[2]),
                        Rd5DiffTool()
                    )
                }
            } else {
                diff2files(File(args[0]), File(args[1]), File(args[2]))
            }
        }

        private fun readFileIndex(dis: DataInputStream, dos: DataOutputStream?): LongArray {
            val fileIndex = LongArray(25)
            for (i in 0..24) {
                val lv = dis.readLong()
                fileIndex[i] = lv and 0xffffffffffffL
                dos?.writeLong(lv)
            }
            return fileIndex
        }

        private fun getTileStart(index: LongArray, tileIndex: Int): Long {
            return if (tileIndex > 0) index[tileIndex - 1] else 200L
        }

        private fun getTileEnd(index: LongArray, tileIndex: Int): Long {
            return index[tileIndex]
        }

        private fun readPosIndex(dis: DataInputStream, dos: DataOutputStream?): IntArray {
            val posIndex = IntArray(1024)
            for (i in 0..1023) {
                val iv = dis.readInt()
                posIndex[i] = iv
                dos?.writeInt(iv)
            }
            return posIndex
        }

        private fun getPosIdx(posIdx: IntArray, idx: Int): Int {
            return if (idx == -1) 4096 else posIdx[idx]
        }

        private fun createMicroCache(
            posIdx: IntArray?,
            tileIdx: Int,
            dis: DataInputStream,
            deltaMode: Boolean
        ): ByteArray? {
            if (posIdx == null) {
                return null
            }
            var size: Int = getPosIdx(posIdx, tileIdx) - getPosIdx(posIdx, tileIdx - 1)
            if (size == 0) {
                return null
            }
            if (deltaMode) {
                size = dis.readInt()
            }
            val ab = ByteArray(size)
            dis.readFully(ab)
            return ab
        }

        private fun createMicroCache(ab: ByteArray?, dataBuffers: DataBuffers?): MicroCache {
            if (ab == null || ab.isEmpty()) {
                return MicroCache.emptyCache()
            }
            val bc = StatCoderContext(ab)
            return MicroCache2(bc, dataBuffers!!, 0, 0, 32, null, null)
        }

        /**
         * Compute the delta between 2 RD5 files and
         * show statistics on the expected size of the delta file
         */
        public fun diff2files(f1: File, f2: File, outFile: File) {
            val abBuf1 = ByteArray(10 * 1024 * 1024)
            val abBuf2 = ByteArray(10 * 1024 * 1024)

            var nodesDiff = 0
            var diffedTiles = 0
            var bytesDiff = 0L

            val t0 = System.currentTimeMillis()

            DataInputStream(BufferedInputStream(FileInputStream(f1))).use { dis1 ->
            DataInputStream(BufferedInputStream(FileInputStream(f2))).use { dis2 ->
            DataOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { dos ->
                val mcOut = MCOutputStream(dos, abBuf1)

                val fileIndex1: LongArray = readFileIndex(dis1, null)
                val fileIndex2: LongArray = readFileIndex(dis2, dos)

                val dataBuffers = DataBuffers()
                for (subFileIdx in 0..24) {
                    val hasData1 =
                        getTileStart(fileIndex1, subFileIdx) < getTileEnd(fileIndex1, subFileIdx)
                    val hasData2 =
                        getTileStart(fileIndex2, subFileIdx) < getTileEnd(fileIndex2, subFileIdx)

                    val posIdx1: IntArray? = if (hasData1) readPosIndex(dis1, null) else null
                    val posIdx2: IntArray? = if (hasData2) readPosIndex(dis2, dos) else null

                    for (tileIdx in 0..1023) {
                        val ab1: ByteArray? = createMicroCache(posIdx1, tileIdx, dis1, false)
                        val ab2: ByteArray? = createMicroCache(posIdx2, tileIdx, dis2, false)

                        val mc: MicroCache
                        if (ab1.contentEquals(ab2)) {
                            mc = MicroCache.emptyCache()
                        } else {
                            val mc1: MicroCache = createMicroCache(ab1, dataBuffers)
                            val mc2: MicroCache = createMicroCache(ab2, dataBuffers)
                            mc = MicroCache2(mc1.size + mc2.size, abBuf2, 0, 0, 32)
                            mc.calcDelta(mc1, mc2)
                        }

                        val len = mcOut.writeMC(mc)
                        if (len > 0) {
                            bytesDiff += len.toLong()
                            nodesDiff += mc.size
                            diffedTiles++
                        }
                    }
                    mcOut.finish()
                }

                while (true) {
                    val len = dis2.read(abBuf1)
                    if (len < 0) break
                    dos.write(abBuf1, 0, len)
                }
                val t1 = System.currentTimeMillis()
                println("nodesDiff=$nodesDiff bytesDiff=$bytesDiff diffedTiles=$diffedTiles took ${t1 - t0}ms")
            }}}
        }

        public fun recoverFromDelta(
            f1: File,
            f2: File,
            outFile: File,
            progress: ProgressListener
        ) {
            if (f2.length() == 0L) {
                copyFile(f1, outFile, progress)
                return
            }

            val abBuf1 = ByteArray(10 * 1024 * 1024)
            val abBuf2 = ByteArray(10 * 1024 * 1024)

            val t0 = System.currentTimeMillis()

            DataInputStream(BufferedInputStream(FileInputStream(f1))).use { dis1 ->
            DataInputStream(BufferedInputStream(FileInputStream(f2))).use { dis2 ->
            DataOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { dos ->
                val fileIndex1: LongArray = readFileIndex(dis1, null)
                val fileIndex2: LongArray = readFileIndex(dis2, dos)

                var lastPct = -1

                val dataBuffers = DataBuffers()
                val mcIn = MCInputStream(dis2, dataBuffers)

                for (subFileIdx in 0..24) {
                    val hasData1 = getTileStart(fileIndex1, subFileIdx) < getTileEnd(fileIndex1, subFileIdx)
                    val hasData2 = getTileStart(fileIndex2, subFileIdx) < getTileEnd(fileIndex2, subFileIdx)

                    val posIdx1: IntArray? = if (hasData1) readPosIndex(dis1, null) else null
                    val posIdx2: IntArray? = if (hasData2) readPosIndex(dis2, dos) else null

                    for (tileIdx in 0..1023) {
                        if (progress.isCanceled) {
                            return
                        }
                        val bytesProcessed = (getTileStart(
                            fileIndex1,
                            subFileIdx
                        ) + (if (posIdx1 == null) 0 else getPosIdx(posIdx1, tileIdx - 1))).toDouble()
                        val pct = (100.0 * bytesProcessed / getTileEnd(fileIndex1, 24) + 0.5).toInt()
                        if (pct != lastPct) {
                            progress.updateProgress("Applying delta", pct)
                            lastPct = pct
                        }

                        val ab1: ByteArray? = createMicroCache(posIdx1, tileIdx, dis1, false)
                        val mc2 = mcIn.readMC()
                        val targetSize =
                            if (posIdx2 == null) 0 else getPosIdx(posIdx2, tileIdx) - getPosIdx(posIdx2, tileIdx - 1)

                        if (mc2.size == 0) {
                            if (ab1 != null) {
                                dos.write(ab1)
                            }
                            val newTargetSize = ab1?.size ?: 0
                            if (targetSize != newTargetSize) {
                                throw RuntimeException("size mismatch at $subFileIdx/$tileIdx $targetSize!=$newTargetSize")
                            }
                            continue
                        }

                        val mc1: MicroCache = createMicroCache(ab1, dataBuffers)
                        val mc: MicroCache = MicroCache2(mc1.size + mc2.size, abBuf2, 0, 0, 32)
                        mc.addDelta(mc1, mc2, false)

                        if (mc.size() == 0) {
                            if (targetSize != 0) {
                                throw RuntimeException("size mismatch at $subFileIdx/$tileIdx $targetSize>0")
                            }
                            continue
                        }

                        val len = mc.encodeMicroCache(abBuf1)
                        dos.write(abBuf1, 0, len)
                        dos.writeInt(crc(abBuf1, 0, len) xor 2)
                        if (targetSize != len + 4) {
                            throw RuntimeException("size mismatch at $subFileIdx/$tileIdx $targetSize<>${len + 4}")
                        }
                    }
                    mcIn.finish()
                }
                while (true) {
                    val len = dis2.read(abBuf1)
                    if (len < 0) break
                    dos.write(abBuf1, 0, len)
                }
                val t1 = System.currentTimeMillis()
                println("recovering from diffs took ${t1 - t0}ms")
            }}}
            if (progress.isCanceled) outFile.delete()
        }

        public fun copyFile(f1: File, outFile: File, progress: ProgressListener) {
            val sizeTotal = f1.length()
            var sizeRead = 0L
            var lastPct = -1

            DataInputStream(BufferedInputStream(FileInputStream(f1))).use { dis1 ->
            DataOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { dos ->
                val buf = ByteArray(65536)
                while (true) {
                    if (progress.isCanceled) {
                        return
                    }
                    val pct = ((100.0 * sizeRead) / (sizeTotal + 1) + 0.5).toInt()
                    if (pct != lastPct) {
                        progress.updateProgress("Copying", pct)
                        lastPct = pct
                    }
                    val len = dis1.read(buf)
                    if (len <= 0) break
                    sizeRead += len.toLong()
                    dos.write(buf, 0, len)
                }
            }}
            if (progress.isCanceled) outFile.delete()
        }

        public fun addDeltas(f1: File, f2: File, outFile: File) {
            val abBuf1 = ByteArray(10 * 1024 * 1024)
            val abBuf2 = ByteArray(10 * 1024 * 1024)

            val t0 = System.currentTimeMillis()

            DataInputStream(BufferedInputStream(FileInputStream(f1))).use { dis1 ->
            DataInputStream(BufferedInputStream(FileInputStream(f2))).use { dis2 ->
            DataOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { dos ->
                val fileIndex1: LongArray = readFileIndex(dis1, null)
                val fileIndex2: LongArray = readFileIndex(dis2, dos)

                val dataBuffers = DataBuffers()
                val mcIn1 = MCInputStream(dis1, dataBuffers)
                val mcIn2 = MCInputStream(dis2, dataBuffers)
                val mcOut = MCOutputStream(dos, abBuf1)

                for (subFileIdx in 0..24) {
                    val hasData1 =
                        getTileStart(fileIndex1, subFileIdx) < getTileEnd(fileIndex1, subFileIdx)
                    val hasData2 =
                        getTileStart(fileIndex2, subFileIdx) < getTileEnd(fileIndex2, subFileIdx)
                    val posIdx1: IntArray? = if (hasData1) readPosIndex(dis1, null) else null
                    val posIdx2: IntArray? = if (hasData2) readPosIndex(dis2, dos) else null

                    for (tileIdx in 0..1023) {
                        val mc1 = mcIn1.readMC()
                        val mc2 = mcIn2.readMC()
                        val mc: MicroCache = if (mc1.size == 0 && mc2.size == 0) {
                            mc1
                        } else {
                            MicroCache2(mc1.size + mc2.size, abBuf2, 0, 0, 32).also { it.addDelta(mc1, mc2, true) }
                        }
                        mcOut.writeMC(mc)
                    }
                    mcIn1.finish()
                    mcIn2.finish()
                    mcOut.finish()
                }
                while (true) {
                    val len = dis2.read(abBuf1)
                    if (len < 0) break
                    dos.write(abBuf1, 0, len)
                }
                val t1 = System.currentTimeMillis()
                println("adding diffs took ${t1 - t0}ms")
            }}}
        }

        public fun reEncode(f1: File, outFile: File) {
            val abBuf1 = ByteArray(10 * 1024 * 1024)

            val t0 = System.currentTimeMillis()

            DataInputStream(BufferedInputStream(FileInputStream(f1))).use { dis1 ->
            DataOutputStream(BufferedOutputStream(FileOutputStream(outFile))).use { dos ->
                val fileIndex1: LongArray = readFileIndex(dis1, dos)

                val dataBuffers = DataBuffers()
                for (subFileIdx in 0..24) {
                    val hasData1 =
                        getTileStart(fileIndex1, subFileIdx) < getTileEnd(fileIndex1, subFileIdx)

                    val posIdx1: IntArray? = if (hasData1) readPosIndex(dis1, dos) else null

                    for (tileIdx in 0..1023) {
                        val ab1: ByteArray? = createMicroCache(posIdx1, tileIdx, dis1, false)
                        if (ab1 == null) continue

                        val mc1: MicroCache = createMicroCache(ab1, dataBuffers)
                        val len = mc1.encodeMicroCache(abBuf1)
                        dos.write(abBuf1, 0, len)
                        dos.writeInt(crc(abBuf1, 0, len) xor 2)
                    }
                }
                while (true) {
                    val len = dis1.read(abBuf1)
                    if (len < 0) break
                    dos.write(abBuf1, 0, len)
                }
                val t1 = System.currentTimeMillis()
                println("re-encoding took ${t1 - t0}ms")
            }}
        }
    }
}
