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
import org.maplibre.spatialk.geojson.Position
import java.io.DataInput
import java.io.DataOutput

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

    public fun writeToStream(dos: DataOutput) {
        dos.writeInt(node1!!.position.latitude.toIntLatitude())
        dos.writeInt(node1!!.position.longitude.toIntLongitude())
        dos.writeInt(node2!!.position.latitude.toIntLatitude())
        dos.writeInt(node2!!.position.longitude.toIntLongitude())
        dos.writeInt(crosspoint!!.position.latitude.toIntLatitude())
        dos.writeInt(crosspoint!!.position.longitude.toIntLongitude())
        dos.writeInt(waypoint!!.position.latitude.toIntLatitude())
        dos.writeInt(waypoint!!.position.longitude.toIntLongitude())
        dos.writeDouble(radius)
    }

    public companion object {
        public fun readFromStream(dis: DataInput): MatchedWaypoint {
            val mwp = MatchedWaypoint()
            mwp.node1 = OsmNode()
            mwp.node2 = OsmNode()
            mwp.crosspoint = OsmNode()
            mwp.waypoint = OsmNode()

            val node1Lat = dis.readInt()
            val node1Lon = dis.readInt()
            val node2Lat = dis.readInt()
            val node2Lon = dis.readInt()
            val crosspointLat = dis.readInt()
            val crosspointLon = dis.readInt()
            val waypointLat = dis.readInt()
            val waypointLon = dis.readInt()

            mwp.node1!!.position = Position(node1Lon.toDoubleLongitude(), node1Lat.toDoubleLatitude(), 0.0)
            mwp.node2!!.position = Position(node2Lon.toDoubleLongitude(), node2Lat.toDoubleLatitude(), 0.0)
            mwp.crosspoint!!.position = Position(crosspointLon.toDoubleLongitude(), crosspointLat.toDoubleLatitude(), 0.0)
            mwp.waypoint!!.position = Position(waypointLon.toDoubleLongitude(), waypointLat.toDoubleLatitude(), 0.0)
            mwp.radius = dis.readDouble()
            return mwp
        }
    }
}
