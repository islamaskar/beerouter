/**
 * Information on matched way point
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import kotlinx.io.Sink
import kotlinx.io.Source
import org.maplibre.spatialk.geojson.Position

public class MatchedWaypoint {
    public enum class Type {
        /** route next to this point */
        SHAPING,

        /** visit this point  */
        MEETING,

        /** from this point go direct to next = beeline routing  */
        DIRECT
    }

    public var node1: OsmNode? = null
    public var node2: OsmNode? = null
    public var crosspoint: OsmNode? = null
    public var waypoint: OsmNode? = null
    public var correctedpoint: OsmNode? = null
    public var name: String? = null // waypoint name used in error messages
    public var radius: Double = 0.0 // distance in meter between waypoint and crosspoint
    public var type: Type = Type.SHAPING
    public var indexInTrack: Int = 0
    public var directionToNext: Double = -1.0
    public var directionDiff: Double = 361.0

    public var wayNearest: MutableList<MatchedWaypoint> = mutableListOf()
    public var hasUpdate: Boolean = false

    public fun writeToStream(sink: Sink) {
        val n1 = requireNotNull(node1)
        val n2 = requireNotNull(node2)
        val cp = requireNotNull(crosspoint)
        val wp = requireNotNull(waypoint)
        sink.writeInt(n1.position.latitude.toIntLatitude())
        sink.writeInt(n1.position.longitude.toIntLongitude())
        sink.writeInt(n2.position.latitude.toIntLatitude())
        sink.writeInt(n2.position.longitude.toIntLongitude())
        sink.writeInt(cp.position.latitude.toIntLatitude())
        sink.writeInt(cp.position.longitude.toIntLongitude())
        sink.writeInt(wp.position.latitude.toIntLatitude())
        sink.writeInt(wp.position.longitude.toIntLongitude())
        sink.writeLong(radius.toBits())
    }

    public companion object {
        public fun readFromStream(source: Source): MatchedWaypoint {
            val node1Lat = source.readInt()
            val node1Lon = source.readInt()
            val node2Lat = source.readInt()
            val node2Lon = source.readInt()
            val crosspointLat = source.readInt()
            val crosspointLon = source.readInt()
            val waypointLat = source.readInt()
            val waypointLon = source.readInt()

            val node1 = OsmNode().also { it.position = Position(node1Lon.toDoubleLongitude(), node1Lat.toDoubleLatitude(), 0.0) }
            val node2 = OsmNode().also { it.position = Position(node2Lon.toDoubleLongitude(), node2Lat.toDoubleLatitude(), 0.0) }
            val crosspoint = OsmNode().also { it.position = Position(crosspointLon.toDoubleLongitude(), crosspointLat.toDoubleLatitude(), 0.0) }
            val waypoint = OsmNode().also { it.position = Position(waypointLon.toDoubleLongitude(), waypointLat.toDoubleLatitude(), 0.0) }

            val mwp = MatchedWaypoint()
            mwp.node1 = node1
            mwp.node2 = node2
            mwp.crosspoint = crosspoint
            mwp.waypoint = waypoint
            mwp.radius = Double.fromBits(source.readLong())
            return mwp
        }
    }
}
