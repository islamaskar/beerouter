package dev.skynomads.beerouter.mapcreator

import androidx.collection.MutableLongIntMap
import dev.skynomads.beerouter.mapcreator.DiffCoderDataOutputStream
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.File
import java.io.FileInputStream

public class WayCutter5 : MapCreatorBase() {
    private lateinit var borderNidsOutStream: DiffCoderDataOutputStream
    private lateinit var tileIndexMap: MutableLongIntMap
    private lateinit var nodeTilesIn: File
    private var lonoffset: Int = -1
    private var latoffset: Int = -1

    public var relMerger: RelationMerger? = null
    public var nodeFilter: NodeFilter? = null
    public var nodeCutter: NodeCutter? = null
    public var restrictionCutter5: RestrictionCutter5? = null

    public fun process(nodeTilesIn: File, wayTilesIn: File, wayTilesOut: File, borderNidsOut: File) {
        this.nodeTilesIn = nodeTilesIn
        this.outTileDir = wayTilesOut
        borderNidsOutStream = createOutStream(borderNidsOut)
        WayIterator(this, true).processDir(wayTilesIn, ".wtl")
        borderNidsOutStream.close()
    }

    override fun wayFileStart(wayfile: File): Boolean {
        val nodefilename = wayfile.name.substring(0, wayfile.name.length - 3) + "ntl"
        val nodefile = File(nodeTilesIn, nodefilename)
        tileIndexMap = MutableLongIntMap()
        lonoffset = -1
        latoffset = -1
        nodeCutter?.nodeFileStart(null)
        NodeIterator(this, nodeCutter != null).processFile(nodefile)
        restrictionCutter5?.let { cutter ->
            val resfilename = wayfile.name.substring(0, wayfile.name.length - 3) + "rtl"
            val resfile = File("restrictions", resfilename)
            if (resfile.exists()) {
                val di = DataInputStream(BufferedInputStream(FileInputStream(resfile)))
                try {
                    while (true) {
                        cutter.nextRestriction(RestrictionData(di))
                    }
                } catch (_: EOFException) {
                    di.close()
                }
            }
        }
        return true
    }

    override fun nextNode(data: NodeData) {
        if (nodeFilter?.isRelevant(data) == false) {
            return
        }
        nodeCutter?.nextNode(data)
        tileIndexMap[data.nid] = getTileIndex(data.ilon, data.ilat)
    }

    override fun nextWay(data: WayData) {
        var waytileset = 0L
        val tiForNode = IntArray(data.nodes.size)
        for (i in 0 until data.nodes.size) {
            val tileIndex = tileIndexMap.getOrDefault(data.nodes.get(i), -1)
            if (tileIndex != -1) {
                waytileset = waytileset or (1L shl tileIndex)
            }
            tiForNode[i] = tileIndex
        }
        relMerger?.nextWay(data)
        for (tileIndex in 0 until 54) {
            if ((waytileset and (1L shl tileIndex)) != 0L) {
                data.writeTo(getOutStreamForTile(tileIndex))
            }
        }
        for (i in 0 until data.nodes.size) {
            val ti = tiForNode[i]
            if (ti != -1 && ((i > 0 && tiForNode[i - 1] != ti) || (i + 1 < data.nodes.size && tiForNode[i + 1] != ti))) {
                writeId(borderNidsOutStream, data.nodes.get(i))
            }
        }
    }

    override fun wayFileEnd(wayfile: File) {
        closeTileOutStreams()
        nodeCutter?.nodeFileEnd(null)
        restrictionCutter5?.finish()
    }

    public fun getTileIndexForNid(nid: Long): Int = tileIndexMap.getOrDefault(nid, -1)

    private fun getTileIndex(ilon: Int, ilat: Int): Int {
        val lonoff = (ilon / 45000000) * 45
        val latoff = (ilat / 30000000) * 30
        if (lonoffset == -1) lonoffset = lonoff
        if (latoffset == -1) latoffset = latoff
        if (lonoff != lonoffset || latoff != latoffset) {
            throw IllegalArgumentException("inconsistent node: $ilon $ilat")
        }
        val lon = (ilon / 5000000) % 9
        val lat = (ilat / 5000000) % 6
        if (lon !in 0..8 || lat !in 0..5) {
            throw IllegalArgumentException("illegal pos: $ilon,$ilat")
        }
        return lon * 6 + lat
    }

    public override fun getNameForTile(tileIndex: Int): String {
        val lon = (tileIndex / 6) * 5 + lonoffset - 180
        val lat = (tileIndex % 6) * 5 + latoffset - 90
        val slon = if (lon < 0) "W${-lon}" else "E$lon"
        val slat = if (lat < 0) "S${-lat}" else "N$lat"
        return "${slon}_${slat}.wt5"
    }
}
