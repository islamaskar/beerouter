package dev.skynomads.beerouter.map.generator

import androidx.collection.MutableLongIntMap
import java.io.File

public class WayCutter : GeneratorBase() {
    private lateinit var tileIndexMap: MutableLongIntMap

    public fun process(nodeTilesIn: File, wayFileIn: File, wayTilesOut: File) {
        init(wayTilesOut)
        NodeIterator(this, false).processDir(nodeTilesIn, ".tlf")
        WayIterator(this, true).processFile(wayFileIn)
        finish()
    }

    public fun init(wayTilesOut: File) {
        outTileDir = wayTilesOut
        tileIndexMap = MutableLongIntMap()
    }

    public fun finish() {
        closeTileOutStreams()
    }

    override fun nextNode(data: NodeData) {
        tileIndexMap[data.nid] = getTileIndex(data.ilon, data.ilat)
    }

    override fun nextWay(data: WayData) {
        var waytileset = 0L
        for (i in 0 until data.nodes.size) {
            val tileIndex = tileIndexMap.getOrDefault(data.nodes.get(i), -1)
            if (tileIndex != -1) {
                waytileset = waytileset or (1L shl tileIndex)
            }
        }
        for (tileIndex in 0 until 54) {
            if ((waytileset and (1L shl tileIndex)) != 0L) {
                data.writeTo(getOutStreamForTile(tileIndex))
            }
        }
    }

    public fun getTileIndexForNid(nid: Long): Int = tileIndexMap.getOrDefault(nid, -1)

    private fun getTileIndex(ilon: Int, ilat: Int): Int {
        val lon = ilon / 45000000
        val lat = ilat / 30000000
        if (lon !in 0..7 || lat !in 0..5) {
            throw IllegalArgumentException("illegal pos: $ilon,$ilat")
        }
        return lon * 6 + lat
    }

    public override fun getNameForTile(tileIndex: Int): String {
        val lon = (tileIndex / 6) * 45 - 180
        val lat = (tileIndex % 6) * 30 - 90
        val slon = if (lon < 0) "W${-lon}" else "E$lon"
        val slat = if (lat < 0) "S${-lat}" else "N$lat"
        return "${slon}_${slat}.wtl"
    }
}
