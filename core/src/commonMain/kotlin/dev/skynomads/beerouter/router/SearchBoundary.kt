/**
 * static helper class for handling datafiles
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.OsmNode
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude

internal class SearchBoundary(n: OsmNode, private val radius: Int, val direction: Int) {
    private enum class Direction(public val id: Int) {
        NORTH(0),
        WEST(1),
        SOUTH(2),
        EAST(3);

        companion object {
            fun fromId(id: Int): Direction =
                entries.getOrElse(id) {
                    throw IllegalArgumentException("undefined direction: $id")
                }
        }
    }

    private val minlon0: Int
    private val minlat0: Int
    private val maxlon0: Int
    private val maxlat0: Int

    private val minlon: Int
    private val minlat: Int
    private val maxlon: Int
    private val maxlat: Int
    private val p: OsmNode = OsmNode(n.position)
    private val searchDirection: Direction = Direction.fromId(direction)

    /**
     * @param radius Search radius in meters.
     */
    init {
        val (lon, lat) = tileOrigin(n)

        minlon0 = lon - 5000000
        minlat0 = lat - 5000000
        maxlon0 = lon + 10000000
        maxlat0 = lat + 10000000

        minlon = lon - 1000000
        minlat = lat - 1000000
        maxlon = lon + 6000000
        maxlat = lat + 6000000
    }

    fun isInBoundary(n: OsmNode, cost: Int): Boolean {
        if (radius > 0) {
            return n.calcDistance(p) < radius
        }
        val nLon = n.position.longitude.toIntLongitude()
        val nLat = n.position.latitude.toIntLatitude()
        if (cost == 0) {
            return nLon in (minlon0 + 1)..<maxlon0 && nLat > minlat0 && nLat < maxlat0
        }
        return nLon in (minlon + 1)..<maxlon && nLat > minlat && nLat < maxlat
    }

    fun getBoundaryDistance(n: OsmNode): Int {
        val nLon = n.position.longitude.toIntLongitude()
        val nLat = n.position.latitude.toIntLatitude()
        return when (searchDirection) {
            Direction.NORTH -> n.calcDistance(OsmNode(nLon, minlat))
            Direction.WEST -> n.calcDistance(OsmNode(minlon, nLat))
            Direction.SOUTH -> n.calcDistance(OsmNode(nLon, maxlat))
            Direction.EAST -> n.calcDistance(OsmNode(maxlon, nLat))
        }
    }

    companion object {
        private fun tileOrigin(n: OsmNode): Pair<Int, Int> {
            val lon = (n.position.longitude.toIntLongitude() / 5000000) * 5000000
            val lat = (n.position.latitude.toIntLatitude() / 5000000) * 5000000
            return lon to lat
        }

        fun getFileName(n: OsmNode): String {
            val (lon, lat) = tileOrigin(n)

            val dlon = lon / 1000000 - 180
            val dlat = lat / 1000000 - 90

            val slon = if (dlon < 0) "W${-dlon}" else "E$dlon"
            val slat = if (dlat < 0) "S${-dlat}" else "N$dlat"
            return "${slon}_${slat}.trf"
        }
    }
}
