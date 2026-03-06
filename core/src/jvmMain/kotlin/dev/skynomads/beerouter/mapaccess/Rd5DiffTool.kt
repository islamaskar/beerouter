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
import java.io.IOException

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

        @Throws(Exception::class)
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

        @Throws(Exception::class)
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

        @Throws(IOException::class)
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
        @Throws(Exception::class)
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
                        Rd5DiffTool() /*, new File( args[3] ) */
                    )
                }
            } else {
                diff2files(File(args[0]), File(args[1]), File(args[2]))
            }
        }

        @Throws(IOException::class)
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

        @Throws(IOException::class)
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

        @Throws(IOException::class)
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
        @Throws(Exception::class)
        public fun diff2files(f1: File, f2: File, outFile: File) {
            val abBuf1 = ByteArray(10 * 1024 * 1024)
            val abBuf2 = ByteArray(10 * 1024 * 1024)

            var nodesDiff = 0
            var diffedTiles = 0

            var bytesDiff = 0L

            val dis1 = DataInputStream(BufferedInputStream(FileInputStream(f1)))
            val dis2 = DataInputStream(BufferedInputStream(FileInputStream(f2)))
            val dos = DataOutputStream(BufferedOutputStream(FileOutputStream(outFile)))
            val mcOut = MCOutputStream(dos, abBuf1)

            // copy header to outfile
            val fileIndex1: LongArray = readFileIndex(dis1, null)
            val fileIndex2: LongArray = readFileIndex(dis2, dos)

            val t0 = System.currentTimeMillis()

            try {
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

                        val mc: MicroCache?
                        if (ab1.contentEquals(ab2)) {
                            mc = MicroCache.emptyCache() // empty diff
                        } else  // calc diff of the 2 tiles
                        {
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

                            /*                 // do some consistemcy checks on the encoding

                 byte[] bytes = new byte[len];
                 abBuf1.copyInto(bytes, 0, 0, len);

                 // cross-check the encoding: decode again
                 MicroCache mcCheck = new MicroCache2( new StatCoderContext( bytes ), new DataBuffers( null ), 0, 0, 32, null, null );

                 // due to link-order ambiguity, for decoded we can only compare node-count and datasize
                 if ( mc.size() != mcCheck.size() )
                 {
                   throw new IllegalArgumentException( "re-decoded data-size mismatch!" );
                 }
                 if ( mc.getSize() != mcCheck.getSize() )
                 {
                   throw new IllegalArgumentException( "re-decoded node-count mismatch!" );
                 }

                 // .... so re-encode again
                 int len2 = mcCheck.encodeMicroCache( abBuf1 );
                 byte[] bytes2 = new byte[len2];
                 abBuf1.copyInto(bytes2, 0, 0, len2);

                 // and here we can compare byte-by-byte
                 if ( len != len2 )
                 {
                   throw new IllegalArgumentException( "decoded size mismatch!" );
                 }
                 for( int i=0; i<len; i++ )
                 {
                   if ( bytes[i] != bytes2[i] )
                   {
                     throw new IllegalArgumentException( "decoded data mismatch at i=" + i );
                   }
                 }
             */
                        }
                    }
                    mcOut.finish()
                }

                // write any remaining data to the output file
                while (true) {
                    val len = dis2.read(abBuf1)
                    if (len < 0) {
                        break
                    }
                    dos.write(abBuf1, 0, len)
                }
                val t1 = System.currentTimeMillis()
                println("nodesDiff=" + nodesDiff + " bytesDiff=" + bytesDiff + " diffedTiles=" + diffedTiles + " took " + (t1 - t0) + "ms")
            } finally {
                if (dis1 != null) {
                    try {
                        dis1.close()
                    } catch (ee: Exception) {
                    }
                }
                if (dis2 != null) {
                    try {
                        dis2.close()
                    } catch (ee: Exception) {
                    }
                }
                if (dos != null) {
                    try {
                        dos.close()
                    } catch (ee: Exception) {
                    }
                }
            }
        }


        @Throws(IOException::class)
        public fun recoverFromDelta(
            f1: File,
            f2: File,
            outFile: File,
            progress: ProgressListener /* , File cmpFile */
        ) {
            if (f2.length() == 0L) {
                copyFile(f1, outFile, progress)
                return
            }

            val abBuf1 = ByteArray(10 * 1024 * 1024)
            val abBuf2 = ByteArray(10 * 1024 * 1024)

            var canceled = false

            val t0 = System.currentTimeMillis()

            val dis1 = DataInputStream(BufferedInputStream(FileInputStream(f1)))
            val dis2 = DataInputStream(BufferedInputStream(FileInputStream(f2)))
            //    DataInputStream disCmp = new DataInputStream( new BufferedInputStream( new FileInputStream( cmpFile ) ) );
            val dos = DataOutputStream(BufferedOutputStream(FileOutputStream(outFile)))

            // copy header to outfile
            val fileIndex1: LongArray = readFileIndex(dis1, null)
            val fileIndex2: LongArray = readFileIndex(dis2, dos)

            //    long[] fileIndexCmp = readFileIndex( disCmp, null );
            var lastPct = -1

            try {
                val dataBuffers = DataBuffers()
                val mcIn = MCInputStream(dis2, dataBuffers)

                for (subFileIdx in 0..24) {
                    val hasData1 = getTileStart(fileIndex1, subFileIdx) < getTileEnd(
                        fileIndex1,
                        subFileIdx
                    ) // has the basefile data
                    val hasData2 = getTileStart(fileIndex2, subFileIdx) < getTileEnd(
                        fileIndex2,
                        subFileIdx
                    ) // has the *result* data

                    //         boolean hasDataCmp = getTileStart( fileIndexCmp, subFileIdx ) < getTileEnd( fileIndexCmp, subFileIdx );
                    val posIdx1: IntArray? = if (hasData1) readPosIndex(dis1, null) else null
                    val posIdx2: IntArray? = if (hasData2) readPosIndex(dis2, dos) else null

                    //        int[] posIdxCmp = hasDataCmp ? readPosIndex( disCmp, null ) : null;
                    for (tileIdx in 0..1023) {
                        if (progress.isCanceled) {
                            canceled = true
                            return
                        }
                        val bytesProcessed = (getTileStart(
                            fileIndex1,
                            subFileIdx
                        ) + (if (posIdx1 == null) 0 else getPosIdx(
                            posIdx1,
                            tileIdx - 1
                        ))).toDouble()
                        val pct =
                            (100.0 * bytesProcessed / getTileEnd(fileIndex1, 24) + 0.5).toInt()
                        if (pct != lastPct) {
                            progress.updateProgress("Applying delta", pct)
                            lastPct = pct
                        }

                        val ab1: ByteArray? = createMicroCache(posIdx1, tileIdx, dis1, false)
                        val mc2 = mcIn.readMC()
                        val targetSize =
                            if (posIdx2 == null) 0 else getPosIdx(posIdx2, tileIdx) - getPosIdx(
                                posIdx2,
                                tileIdx - 1
                            )

                        /*         int targetSizeCmp = getPosIdx( posIdxCmp, tileIdx ) - getPosIdx( posIdxCmp, tileIdx-1 );
           if ( targetSizeCmp != targetSize ) throw new IllegalArgumentException( "target size mismatch: "+ targetSize + "," + targetSizeCmp );
           byte[] abCmp = new byte[targetSizeCmp];
           disCmp.readFully( abCmp );
*/

                        // no-delta shortcut: just copy base data
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

                        // this is the real delta case (using decode->delta->encode )
                        val mc1: MicroCache = createMicroCache(ab1, dataBuffers)

                        val mc: MicroCache =
                            MicroCache2(mc1.size + mc2.size, abBuf2, 0, 0, 32)
                        mc.addDelta(mc1, mc2, false)

                        if (mc.size() == 0) {
                            if (targetSize != 0) {
                                throw RuntimeException("size mismatch at $subFileIdx/$tileIdx $targetSize>0")
                            }
                            continue
                        }

                        val len = mc.encodeMicroCache(abBuf1)

                        /*           System.out.println( "comparing for subFileIdx=" + subFileIdx + " tileIdx=" + tileIdx );
           boolean isequal = true;
           for( int i=0; i<len;i++ )
           {
             if ( isequal && abCmp[i] != abBuf1[i] )
             {
               System.out.println( "data mismatch at i=" + i + " " + abCmp[i] + "!=" + abBuf1[i]  + " targetSize=" + targetSize );
               isequal = false;

               MicroCache.debug = true;
               System.out.println( "**** decoding original cache ****" );
               createMicroCache( abCmp, dataBuffers );
               System.out.println( "**** decoding reconstructed cache ****" );
               createMicroCache( abBuf1, dataBuffers );
               System.exit(1);
             }
           }
*/
                        dos.write(abBuf1, 0, len)
                        dos.writeInt(crc(abBuf1, 0, len) xor 2)
                        if (targetSize != len + 4) {
                            throw RuntimeException("size mismatch at " + subFileIdx + "/" + tileIdx + " " + targetSize + "<>" + (len + 4))
                        }
                    }
                    mcIn.finish()
                }
                // write any remaining data to the output file
                while (true) {
                    val len = dis2.read(abBuf1)
                    if (len < 0) {
                        break
                    }
                    dos.write(abBuf1, 0, len)
                }
                val t1 = System.currentTimeMillis()
                println("recovering from diffs took " + (t1 - t0) + "ms")
            } finally {
                if (dis1 != null) {
                    try {
                        dis1.close()
                    } catch (ee: Exception) {
                    }
                }
                if (dis2 != null) {
                    try {
                        dis2.close()
                    } catch (ee: Exception) {
                    }
                }
                if (dos != null) {
                    try {
                        dos.close()
                    } catch (ee: Exception) {
                    }
                    if (canceled) {
                        outFile.delete()
                    }
                }
            }
        }

        @Throws(IOException::class)
        public fun copyFile(f1: File, outFile: File, progress: ProgressListener) {
            var canceled = false
            val dis1 = DataInputStream(BufferedInputStream(FileInputStream(f1)))
            val dos = DataOutputStream(BufferedOutputStream(FileOutputStream(outFile)))
            var lastPct = -1
            val sizeTotal = f1.length()
            var sizeRead = 0L
            try {
                val buf: ByteArray? = ByteArray(65536)
                while (true) {
                    if (progress.isCanceled) {
                        canceled = true
                        return
                    }
                    val pct = ((100.0 * sizeRead) / (sizeTotal + 1) + 0.5).toInt()
                    if (pct != lastPct) {
                        progress.updateProgress("Copying", pct)
                        lastPct = pct
                    }
                    val len = dis1.read(buf)
                    if (len <= 0) {
                        break
                    }
                    sizeRead += len.toLong()
                    dos.write(buf, 0, len)
                }
            } finally {
                if (dis1 != null) {
                    try {
                        dis1.close()
                    } catch (ee: Exception) {
                    }
                }
                if (dos != null) {
                    try {
                        dos.close()
                    } catch (ee: Exception) {
                    }
                    if (canceled) {
                        outFile.delete()
                    }
                }
            }
        }

        @Throws(Exception::class)
        public fun addDeltas(f1: File, f2: File, outFile: File) {
            val abBuf1 = ByteArray(10 * 1024 * 1024)
            val abBuf2 = ByteArray(10 * 1024 * 1024)

            val dis1 = DataInputStream(BufferedInputStream(FileInputStream(f1)))
            val dis2 = DataInputStream(BufferedInputStream(FileInputStream(f2)))
            val dos = DataOutputStream(BufferedOutputStream(FileOutputStream(outFile)))

            // copy subfile-header to outfile
            val fileIndex1: LongArray = readFileIndex(dis1, null)
            val fileIndex2: LongArray = readFileIndex(dis2, dos)

            val t0 = System.currentTimeMillis()

            try {
                val dataBuffers = DataBuffers()
                val mcIn1 = MCInputStream(dis1, dataBuffers)
                val mcIn2 = MCInputStream(dis2, dataBuffers)
                val mcOut = MCOutputStream(dos, abBuf1)

                for (subFileIdx in 0..24) {
                    // copy tile-header to outfile
                    val hasData1 =
                        getTileStart(fileIndex1, subFileIdx) < getTileEnd(fileIndex1, subFileIdx)
                    val hasData2 =
                        getTileStart(fileIndex2, subFileIdx) < getTileEnd(fileIndex2, subFileIdx)
                    val posIdx1: IntArray? = if (hasData1) readPosIndex(dis1, null) else null
                    val posIdx2: IntArray? = if (hasData2) readPosIndex(dis2, dos) else null

                    for (tileIdx in 0..1023) {
                        val mc1 = mcIn1.readMC()
                        val mc2 = mcIn2.readMC()
                        val mc: MicroCache?
                        if (mc1.size == 0 && mc2.size == 0) {
                            mc = mc1
                        } else {
                            mc = MicroCache2(mc1.size + mc2.size, abBuf2, 0, 0, 32)
                            mc.addDelta(mc1, mc2, true)
                        }
                        mcOut.writeMC(mc)
                    }
                    mcIn1.finish()
                    mcIn2.finish()
                    mcOut.finish()
                }
                // write any remaining data to the output file
                while (true) {
                    val len = dis2.read(abBuf1)
                    if (len < 0) {
                        break
                    }
                    dos.write(abBuf1, 0, len)
                }
                val t1 = System.currentTimeMillis()
                println("adding diffs took " + (t1 - t0) + "ms")
            } finally {
                if (dis1 != null) {
                    try {
                        dis1.close()
                    } catch (ee: Exception) {
                    }
                }
                if (dis2 != null) {
                    try {
                        dis2.close()
                    } catch (ee: Exception) {
                    }
                }
                if (dos != null) {
                    try {
                        dos.close()
                    } catch (ee: Exception) {
                    }
                }
            }
        }


        @Throws(Exception::class)
        public fun reEncode(f1: File, outFile: File) {
            val abBuf1 = ByteArray(10 * 1024 * 1024)

            val dis1 = DataInputStream(BufferedInputStream(FileInputStream(f1)))
            val dos = DataOutputStream(BufferedOutputStream(FileOutputStream(outFile)))

            // copy header to outfile
            val fileIndex1: LongArray = readFileIndex(dis1, dos)

            val t0 = System.currentTimeMillis()

            try {
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
                // write any remaining data to the output file
                while (true) {
                    val len = dis1.read(abBuf1)
                    if (len < 0) {
                        break
                    }
                    dos.write(abBuf1, 0, len)
                }
                val t1 = System.currentTimeMillis()
                println("re-encoding took " + (t1 - t0) + "ms")
            } finally {
                if (dis1 != null) {
                    try {
                        dis1.close()
                    } catch (ee: Exception) {
                    }
                }
                if (dos != null) {
                    try {
                        dos.close()
                    } catch (ee: Exception) {
                    }
                }
            }
        }
    }
}
