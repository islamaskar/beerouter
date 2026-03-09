package dev.skynomads.beerouter.mapcreator

import androidx.collection.MutableLongSet
import dev.skynomads.beerouter.mapcreator.DiffCoderDataOutputStream
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream

public class PosUnifier : MapCreatorBase() {
    private lateinit var nodesOutStream: DiffCoderDataOutputStream
    private lateinit var borderNodesOut: DiffCoderDataOutputStream
    private lateinit var nodeTilesOut: File
    private var outNodeFile: File? = null
    private lateinit var positionSets: Array<MutableLongSet?>
    private var srtmmap: MutableMap<String, ElevationRaster> = HashMap()
    private var lastSrtmLonIdx: Int = -1
    private var lastSrtmLatIdx: Int = -1
    private var lastSrtmRaster: ElevationRaster? = null
    private lateinit var srtmdir: String
    private var srtmfallbackdir: String? = null
    private lateinit var borderNids: MutableLongSet

    public fun process(
        nodeTilesIn: File,
        nodeTilesOut: File,
        bordernidsinfile: File,
        bordernodesoutfile: File,
        srtmdir: String,
        srtmfallbackdir: String?,
    ) {
        this.nodeTilesOut = nodeTilesOut
        this.srtmdir = srtmdir
        this.srtmfallbackdir = srtmfallbackdir
        val dis: DataInputStream = createInStream(bordernidsinfile)
        val mutableBorderNids = MutableLongSet()
        try {
            while (true) {
                val nid = readId(dis)
                if (!mutableBorderNids.contains(nid)) {
                    mutableBorderNids.add(nid)
                }
            }
        } catch (_: EOFException) {
            dis.close()
        }
        borderNids = mutableBorderNids
        borderNodesOut = createOutStream(bordernodesoutfile)
        NodeIterator(this, true).processDir(nodeTilesIn, ".n5d")
        borderNodesOut.close()
    }

    override fun nodeFileStart(nodefile: File?) {
        resetElevationRaster()
        outNodeFile = fileFromTemplate(requireNotNull(nodefile), nodeTilesOut, "u5d")
        nodesOutStream = createOutStream(requireNotNull(outNodeFile))
        positionSets = arrayOfNulls(2500)
    }

    override fun nextNode(data: NodeData) {
        data.selev = Short.MIN_VALUE
        val srtm = srtmForNode(data.ilon, data.ilat)
        if (srtm != null) {
            data.selev = srtm.getElevation(data.ilon, data.ilat)
        }
        findUniquePos(data)
        data.writeTo(nodesOutStream)
        if (borderNids.contains(data.nid)) {
            data.writeTo(borderNodesOut)
        }
    }

    override fun nodeFileEnd(nodefile: File?) {
        nodesOutStream.close()
        val raster = lastSrtmRaster
        val output = outNodeFile
        if (output != null && raster != null) {
            val newName = output.absolutePath + if (raster.nrows > 6001) "_1" else "_3"
            output.renameTo(File(newName))
        }
        resetElevationRaster()
    }

    private fun checkAdd(lon: Int, lat: Int): Boolean {
        val slot = ((lon % 5_000_000) / 100_000) * 50 + ((lat % 5_000_000) / 100_000)
        val id = (lon.toLong() shl 32) or (lat.toLong() and 0xffffffffL)
        var set = positionSets[slot]
        if (set == null) {
            set = MutableLongSet()
            positionSets[slot] = set
        }
        return if (!set.contains(id)) {
            set.add(id)
            true
        } else {
            false
        }
    }

    private fun findUniquePos(node: NodeData) {
        if (!checkAdd(node.ilon, node.ilat)) {
            val lonmod = node.ilon % 1_000_000
            val londelta = if (lonmod < 500_000) 1 else -1
            val latmod = node.ilat % 1_000_000
            val latdelta = if (latmod < 500_000) 1 else -1
            for (latsteps in 0 until 100) {
                for (lonsteps in 0..latsteps) {
                    val lon = node.ilon + lonsteps * londelta
                    val lat = node.ilat + latsteps * latdelta
                    if (checkAdd(lon, lat)) {
                        node.ilon = lon
                        node.ilat = lat
                        return
                    }
                }
            }
            println("*** WARNING: cannot unify position for: ${node.ilon} ${node.ilat}")
        }
    }

    private fun srtmForNode(ilon: Int, ilat: Int): ElevationRaster? {
        val srtmLonIdx = (ilon + 5_000_000) / 5_000_000
        val srtmLatIdx = (654_999_999 - ilat) / 5_000_000 - 100
        if (srtmLonIdx == lastSrtmLonIdx && srtmLatIdx == lastSrtmLatIdx) {
            return lastSrtmRaster
        }
        lastSrtmLonIdx = srtmLonIdx
        lastSrtmLatIdx = srtmLatIdx
        val filename = if (UseRasterRd5FileName) genFilenameRd5(ilon, ilat) else genFilenameXY(srtmLonIdx, srtmLatIdx)
        lastSrtmRaster = srtmmap[filename]
        if (lastSrtmRaster == null && !srtmmap.containsKey(filename)) {
            fun loadRaster(dir: String?): ElevationRaster? {
                if (dir == null) return null
                val file = File(File(dir), "$filename.bef")
                if (!file.exists()) return null
                return try {
                    BufferedInputStream(FileInputStream(file)).use { ElevationRasterCoder().decodeRaster(it) }
                } catch (_: Exception) {
                    null
                }
            }
            lastSrtmRaster = loadRaster(srtmdir) ?: loadRaster(srtmfallbackdir)
            srtmmap[filename] = lastSrtmRaster ?: return null
        }
        return lastSrtmRaster
    }

    private fun resetElevationRaster() {
        srtmmap = HashMap()
        lastSrtmLonIdx = -1
        lastSrtmLatIdx = -1
        lastSrtmRaster = null
    }

    public companion object {
        public const val UseRasterRd5FileName: Boolean = false

        public fun genFilenameXY(srtmLonIdx: Int, srtmLatIdx: Int): String {
            val slonidx = "0$srtmLonIdx"
            val slatidx = "0$srtmLatIdx"
            return "srtm_${slonidx.takeLast(2)}_${slatidx.takeLast(2)}"
        }

        public fun genFilenameRd5(ilon: Int, ilat: Int): String {
            var lonDegree = ilon / 1_000_000
            var latDegree = ilat / 1_000_000
            val lonMod5 = lonDegree % 5
            val latMod5 = latDegree % 5
            lonDegree = lonDegree - 180 - lonMod5
            latDegree = latDegree - 90 - latMod5
            val lonPart = if (lonDegree < 0) "W${-lonDegree}" else "E$lonDegree"
            val latPart = if (latDegree < 0) "S${-latDegree}" else "N$latDegree"
            return "srtm_${lonPart}_${latPart}"
        }
    }
}
