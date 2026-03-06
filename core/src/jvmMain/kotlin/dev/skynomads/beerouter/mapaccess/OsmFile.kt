/**
 * cache for a single square
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.DataBuffers
import dev.skynomads.beerouter.codec.MicroCache
import dev.skynomads.beerouter.codec.MicroCache2
import dev.skynomads.beerouter.codec.StatCoderContext
import dev.skynomads.beerouter.codec.TagValueValidator
import dev.skynomads.beerouter.codec.WaypointMatcher
import dev.skynomads.beerouter.util.ByteDataReader
import dev.skynomads.beerouter.util.Crc32.crc
import java.io.IOException
import java.io.RandomAccessFile

public class OsmFile(
    rafile: PhysicalFile?,
    public var lonDegree: Int,
    public var latDegree: Int,
    dataBuffers: DataBuffers
) {
    private var raf: RandomAccessFile? = null
    private var fileOffset: Long = 0

    private var posIdx: IntArray = IntArray(0)
    private var microCaches: Array<MicroCache?>? = null

    public var filename: String? = null

    private var divisor = 0
    private var cellsize = 0
    private var indexsize = 0
    public var elevationType: Byte = 3

    init {
        val lonMod5 = lonDegree % 5
        val latMod5 = latDegree % 5
        val tileIndex = lonMod5 * 5 + latMod5

        if (rafile != null) {
            divisor = rafile.divisor
            elevationType = rafile.elevationType

            cellsize = 1000000 / divisor
            val ncaches = divisor * divisor
            indexsize = ncaches * 4

            val iobuffer = dataBuffers.iobuffer
            filename = rafile.fileName

            val index = rafile.fileIndex
            fileOffset = if (tileIndex > 0) index[tileIndex - 1] else 200L
            if (fileOffset != index[tileIndex]) {
                raf = rafile.ra
                posIdx = IntArray(ncaches)
                microCaches = arrayOfNulls(ncaches)
                raf!!.seek(fileOffset)
                raf!!.readFully(iobuffer, 0, indexsize)

                val headerCrc = crc(iobuffer, 0, indexsize)
                if (rafile.fileHeaderCrcs[tileIndex] != headerCrc) {
                    throw IOException("sub index checksum error")
                }

                val dis = ByteDataReader(iobuffer)
                for (i in 0..<ncaches) {
                    posIdx[i] = dis.readInt()
                }
            }
        }
    }

    public fun hasData(): Boolean {
        return microCaches != null
    }

    public fun getMicroCache(ilon: Int, ilat: Int): MicroCache? {
        val lonIdx = ilon / cellsize
        val latIdx = ilat / cellsize
        val subIdx = (latIdx - divisor * latDegree) * divisor + (lonIdx - divisor * lonDegree)
        return microCaches!![subIdx]
    }

    public fun createMicroCache(
        ilon: Int,
        ilat: Int,
        dataBuffers: DataBuffers,
        wayValidator: TagValueValidator?,
        waypointMatcher: WaypointMatcher?,
        hollowNodes: OsmNodesMap?
    ): MicroCache {
        val lonIdx = ilon / cellsize
        val latIdx = ilat / cellsize
        val segment = createMicroCache(
            lonIdx, latIdx, dataBuffers, wayValidator, waypointMatcher, true, hollowNodes
        )
        val subIdx = (latIdx - divisor * latDegree) * divisor + (lonIdx - divisor * lonDegree)
        microCaches!![subIdx] = segment
        return segment
    }

    private fun getPosIdx(idx: Int): Int {
        return if (idx == -1) indexsize else posIdx[idx]
    }

    public fun getDataInputForSubIdx(subIdx: Int, iobuffer: ByteArray): Int {
        val startPos = getPosIdx(subIdx - 1)
        val endPos = getPosIdx(subIdx)
        val size = endPos - startPos
        if (size > 0) {
            raf!!.seek(fileOffset + startPos)
            if (size <= iobuffer.size) {
                raf!!.readFully(iobuffer, 0, size)
            }
        }
        return size
    }

    public fun createMicroCache(
        lonIdx: Int, latIdx: Int, dataBuffers: DataBuffers, wayValidator: TagValueValidator?,
        waypointMatcher: WaypointMatcher?, reallyDecode: Boolean, hollowNodes: OsmNodesMap?
    ): MicroCache {
        val subIdx = (latIdx - divisor * latDegree) * divisor + (lonIdx - divisor * lonDegree)

        var ab = dataBuffers.iobuffer
        var asize = getDataInputForSubIdx(subIdx, ab)

        if (asize == 0) {
            return MicroCache.emptyCache()
        }
        if (asize > ab.size) {
            ab = ByteArray(asize)
            asize = getDataInputForSubIdx(subIdx, ab)
        }

        val bc = StatCoderContext(ab)

        try {
            if (!reallyDecode) {
                TODO()
            }
            if (hollowNodes == null) {
                return MicroCache2(bc, dataBuffers, lonIdx, latIdx, divisor, wayValidator, waypointMatcher)
            }
            DirectWeaver(bc, dataBuffers, lonIdx, latIdx, divisor, wayValidator!!, waypointMatcher, hollowNodes)
            return MicroCache.emptyNonVirgin
        } finally {
            // crc check only if the buffer has not been fully read
            val readBytes = (bc.readingBitPosition + 7) shr 3
            if (readBytes != asize - 4) {
                val crcData = crc(ab, 0, asize - 4)
                val crcFooter = ByteDataReader(ab, asize - 4).readInt()
                if (crcData == crcFooter) {
                    throw IOException("old, unsupported data-format")
                } else if ((crcData xor 2) != crcFooter) {
                    throw IOException("checksum error")
                }
            }
        }
    }

    // set this OsmFile to ghost-state:
    public fun setGhostState(): Long {
        val caches = microCaches ?: return 0
        var sum: Long = 0
        for (i in caches.indices) {
            val mc = caches[i] ?: continue
            if (mc.virgin) {
                mc.ghost = true
                sum += mc.dataSize.toLong()
            } else {
                caches[i] = null
            }
        }
        return sum
    }

    public fun collectAll(): Long {
        val caches = microCaches ?: return 0
        var deleted: Long = 0
        for (mc in caches) {
            if (mc != null && !mc.ghost) {
                deleted += mc.collect(0).toLong()
            }
        }
        return deleted
    }

    public fun cleanGhosts(): Long {
        val caches = microCaches ?: return 0
        for (i in caches.indices) {
            if (caches[i]?.ghost == true) {
                caches[i] = null
            }
        }
        return 0
    }

    public fun clean(all: Boolean) {
        val caches = microCaches ?: return
        for (i in caches.indices) {
            val mc = caches[i] ?: continue
            if (all || !mc.virgin) {
                caches[i] = null
            }
        }
    }
}
