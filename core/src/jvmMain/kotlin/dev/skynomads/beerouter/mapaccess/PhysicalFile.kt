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
    public var ra: RandomAccessFile? = null
    public var fileIndex: LongArray = LongArray(25)
    public var fileHeaderCrcs: IntArray = IntArray(0)

    public var creationTime: Long = 0L

    public var fileName: String? = f.getName()

    public var divisor: Int = 80
    public var elevationType: Byte = 3

    init {
        val iobuffer = dataBuffers.iobuffer
        ra = RandomAccessFile(f, "r")
        ra!!.readFully(iobuffer, 0, 200)
        val fileIndexCrc = crc(iobuffer!!, 0, 200)
        var dis = ByteDataReader(iobuffer)
        for (i in 0..24) {
            val lv = dis.readLong()
            val readVersion = (lv shr 48).toShort()
            if (i == 0 && lookupVersion != -1 && readVersion.toInt() != lookupVersion) {
                throw IOException(
                    ("lookup version mismatch (old rd5?) lookups.dat="
                            + lookupVersion + " " + f.getName() + "=" + readVersion)
                )
            }
            fileIndex[i] = lv and 0xffffffffffffL
        }

        // read some extra info from the end of the file, if present
        val len = ra!!.length()

        val pos = fileIndex[24]
        var extraLen = 8 + 26 * 4

        if (len != pos) {
            if ((len - pos) > extraLen) {
                extraLen++
            }

            if (len < pos + extraLen) { // > is o.k. for future extensions!
                throw IOException("file of size " + len + " too short, should be " + (pos + extraLen))
            }

            ra!!.seek(pos)
            ra!!.readFully(iobuffer, 0, extraLen)
            dis = ByteDataReader(iobuffer)
            creationTime = dis.readLong()

            val crcData = dis.readInt()
            divisor = if (crcData == fileIndexCrc) {
                80 // old format
            } else if ((crcData xor 2) == fileIndexCrc) {
                32 // new format
            } else {
                throw IOException("top index checksum error")
            }
            fileHeaderCrcs = IntArray(25)
            for (i in 0..24) {
                fileHeaderCrcs[i] = dis.readInt()
            }
            try {
                elevationType = dis.readByte()
            } catch (e: Exception) {
            }
        }
    }

    public fun close() {
        if (ra != null) {
            try {
                ra!!.close()
            } catch (ee: Exception) {
            }
        }
    }

    public companion object {
        public fun checkVersionIntegrity(f: File): Int {
            var version = -1
            var raf: RandomAccessFile? = null
            try {
                val iobuffer = ByteArray(200)
                raf = RandomAccessFile(f, "r")
                raf.readFully(iobuffer, 0, 200)
                val dis = ByteDataReader(iobuffer)
                val lv = dis.readLong()
                version = (lv shr 48).toInt()
            } catch (e: IOException) {
            } finally {
                try {
                    raf?.close()
                } catch (e: IOException) {
                    throw RuntimeException(e)
                }
            }
            return version
        }

        /**
         * Checks the integrity of the file using the build-in checksums
         *
         * @return the error message if file corrupt, else null
         */
            @Throws(IOException::class)
        public fun checkFileIntegrity(f: File): String? {
            var pf: PhysicalFile? = null
            try {
                val dataBuffers = DataBuffers()
                pf = PhysicalFile(f, dataBuffers, -1, -1)
                val div = pf.divisor
                for (lonDegree in 0..4) { // doesn't really matter..
                    for (latDegree in 0..4) { // ..where on earth we are
                        val osmf = OsmFile(pf, lonDegree, latDegree, dataBuffers)
                        if (osmf.hasData()) for (lonIdx in 0..<div) for (latIdx in 0..<div) osmf.createMicroCache(
                            lonDegree * div + lonIdx,
                            latDegree * div + latIdx,
                            dataBuffers,
                            null,
                            null,
                            true,
                            null
                        )
                    }
                }
            } finally {
                if (pf != null) try {
                    pf.ra!!.close()
                } catch (ee: Exception) {
                }
            }
            return null
        }
    }
}
