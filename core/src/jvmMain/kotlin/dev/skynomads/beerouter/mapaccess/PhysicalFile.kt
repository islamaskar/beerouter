/**
 * cache for a single square
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.DataBuffers
import dev.skynomads.beerouter.util.ByteDataReader
import dev.skynomads.beerouter.util.Crc32.crc
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

public class PhysicalFile(f: File, dataBuffers: DataBuffers, lookupVersion: Int, lookupMinorVersion: Int) {
    public val ra: RandomAccessFile = RandomAccessFile(f, "r")
    public val fileIndex: LongArray = LongArray(25)
    public var fileHeaderCrcs: IntArray = IntArray(0)

    public var creationTime: Long = 0L

    public val fileName: String = f.name

    public var divisor: Int = 80
    public var elevationType: Byte = 3

    init {
        val iobuffer = dataBuffers.iobuffer
        val raf = ra
        raf.readFully(iobuffer, 0, 200)
        val fileIndexCrc = crc(iobuffer, 0, 200)
        var dis = ByteDataReader(iobuffer)
        for (i in 0..24) {
            val lv = dis.readLong()
            val readVersion = (lv shr 48).toShort()
            if (i == 0 && lookupVersion != -1 && readVersion.toInt() != lookupVersion) {
                throw IOException(
                    "lookup version mismatch (old rd5?) lookups.dat=$lookupVersion ${f.name}=$readVersion"
                )
            }
            fileIndex[i] = lv and 0xffffffffffffL
        }

        val len = raf.length()
        val pos = fileIndex[24]
        var extraLen = 8 + 26 * 4

        if (len != pos) {
            if ((len - pos) > extraLen) extraLen++

            if (len < pos + extraLen) {
                throw IOException("file of size $len too short, should be ${pos + extraLen}")
            }

            raf.seek(pos)
            raf.readFully(iobuffer, 0, extraLen)
            dis = ByteDataReader(iobuffer)
            creationTime = dis.readLong()

            val crcData = dis.readInt()
            divisor = when {
                crcData == fileIndexCrc -> 80 // old format
                (crcData xor 2) == fileIndexCrc -> 32 // new format
                else -> throw IOException("top index checksum error")
            }
            fileHeaderCrcs = IntArray(25)
            for (i in 0..24) {
                fileHeaderCrcs[i] = dis.readInt()
            }
            runCatching { elevationType = dis.readByte() }
        }
    }

    public fun close() {
        runCatching { ra.close() }
    }

    public companion object {
        public fun checkVersionIntegrity(f: File): Int {
            return try {
                RandomAccessFile(f, "r").use { raf ->
                    val iobuffer = ByteArray(200)
                    raf.readFully(iobuffer, 0, 200)
                    val dis = ByteDataReader(iobuffer)
                    val lv = dis.readLong()
                    (lv shr 48).toInt()
                }
            } catch (e: Exception) {
                -1
            }
        }

        /**
         * Checks the integrity of the file using the build-in checksums
         *
         * @return the error message if file corrupt, else null
         */
        public fun checkFileIntegrity(f: File): String? {
            val dataBuffers = DataBuffers()
            val pf = PhysicalFile(f, dataBuffers, -1, -1)
            try {
                val div = pf.divisor
                for (lonDegree in 0..4) {
                    for (latDegree in 0..4) {
                        val osmf = OsmFile(pf, lonDegree, latDegree, dataBuffers)
                        if (osmf.hasData()) {
                            for (lonIdx in 0..<div) {
                                for (latIdx in 0..<div) {
                                    osmf.createMicroCacheForCell(
                                        lonDegree * div + lonIdx,
                                        latDegree * div + latIdx,
                                        dataBuffers, null, null, null
                                    )
                                }
                            }
                        }
                    }
                }
            } finally {
                pf.close()
            }
            return null
        }
    }
}
