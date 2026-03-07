/**
 * Information on matched way point
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.osm.latitudeE6
import dev.skynomads.beerouter.osm.longitudeE6
import dev.skynomads.beerouter.osm.positionOf
import kotlinx.io.Sink
import kotlinx.io.Source

public data class MatchedWaypoint(
    public var node1: OsmNode? = null,
    public var node2: OsmNode? = null,
    public var crosspoint: OsmNode? = null,
    public var waypoint: OsmNode? = null,
    public var correctedpoint: OsmNode? = null,
    public var name: String? = null,
    public var radius: Double = 0.0,
    public var type: Type = Type.SHAPING,
    public var indexInTrack: Int = 0,
    public var directionToNext: Double = -1.0,
    public var directionDiff: Double = 361.0,
    public var hasUpdate: Boolean = false
) {
    public enum class Type {
        /** route next to this point */
        SHAPING,

        /** visit this point  */
        MEETING,

        /** from this point go direct to next = beeline routing  */
        DIRECT
    }

    public val wayNearest: MutableList<MatchedWaypoint> = mutableListOf()

    public fun writeToStream(sink: Sink) {
        val n1 = requireNotNull(node1)
        val n2 = requireNotNull(node2)
        val cp = requireNotNull(crosspoint)
        val wp = requireNotNull(waypoint)
        sink.writeInt(n1.position.latitudeE6)
        sink.writeInt(n1.position.longitudeE6)
        sink.writeInt(n2.position.latitudeE6)
        sink.writeInt(n2.position.longitudeE6)
        sink.writeInt(cp.position.latitudeE6)
        sink.writeInt(cp.position.longitudeE6)
        sink.writeInt(wp.position.latitudeE6)
        sink.writeInt(wp.position.longitudeE6)
        sink.writeLong(radius.toBits())
    }

    public companion object {
        private fun nodeFromCoordinates(lon: Int, lat: Int): OsmNode =
            OsmNode(positionOf(lon, lat, 0.0))

        public fun readFromStream(source: Source): MatchedWaypoint {
            val node1Lat = source.readInt()
            val node1Lon = source.readInt()
            val node2Lat = source.readInt()
            val node2Lon = source.readInt()
            val crosspointLat = source.readInt()
            val crosspointLon = source.readInt()
            val waypointLat = source.readInt()
            val waypointLon = source.readInt()

            return MatchedWaypoint().apply {
                node1 = nodeFromCoordinates(node1Lon, node1Lat)
                node2 = nodeFromCoordinates(node2Lon, node2Lat)
                crosspoint = nodeFromCoordinates(crosspointLon, crosspointLat)
                waypoint = nodeFromCoordinates(waypointLon, waypointLat)
                radius = Double.fromBits(source.readLong())
            }
        }
    }
}
