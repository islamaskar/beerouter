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

    public var wayNearest: MutableList<MatchedWaypoint> = ArrayList<MatchedWaypoint>()
    public var hasUpdate: Boolean = false

    public fun writeToStream(sink: Sink) {
        sink.writeInt(node1!!.position.latitude.toIntLatitude())
        sink.writeInt(node1!!.position.longitude.toIntLongitude())
        sink.writeInt(node2!!.position.latitude.toIntLatitude())
        sink.writeInt(node2!!.position.longitude.toIntLongitude())
        sink.writeInt(crosspoint!!.position.latitude.toIntLatitude())
        sink.writeInt(crosspoint!!.position.longitude.toIntLongitude())
        sink.writeInt(waypoint!!.position.latitude.toIntLatitude())
        sink.writeInt(waypoint!!.position.longitude.toIntLongitude())
        sink.writeLong(radius.toBits())
    }

    public companion object {
        public fun readFromStream(source: Source): MatchedWaypoint {
            val mwp = MatchedWaypoint()
            mwp.node1 = OsmNode()
            mwp.node2 = OsmNode()
            mwp.crosspoint = OsmNode()
            mwp.waypoint = OsmNode()

            val node1Lat = source.readInt()
            val node1Lon = source.readInt()
            val node2Lat = source.readInt()
            val node2Lon = source.readInt()
            val crosspointLat = source.readInt()
            val crosspointLon = source.readInt()
            val waypointLat = source.readInt()
            val waypointLon = source.readInt()

            mwp.node1!!.position = Position(node1Lon.toDoubleLongitude(), node1Lat.toDoubleLatitude(), 0.0)
            mwp.node2!!.position = Position(node2Lon.toDoubleLongitude(), node2Lat.toDoubleLatitude(), 0.0)
            mwp.crosspoint!!.position = Position(crosspointLon.toDoubleLongitude(), crosspointLat.toDoubleLatitude(), 0.0)
            mwp.waypoint!!.position = Position(waypointLon.toDoubleLongitude(), waypointLat.toDoubleLatitude(), 0.0)
            mwp.radius = Double.fromBits(source.readLong())
            return mwp
        }
    }
}
