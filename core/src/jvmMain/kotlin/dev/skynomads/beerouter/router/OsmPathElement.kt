package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.OsmPos
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import dev.skynomads.beerouter.osm.toOsmId
import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import dev.skynomads.beerouter.util.CheapRuler.distance
import org.maplibre.spatialk.geojson.Position
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
public class OsmPathElement protected constructor() : OsmPos {
    override public var position: Position = Position(0.0, 0.0, 0.0)

    @Deprecated("Use position.altitude instead")
    public val sElev: Short
        get() = (position.altitude ?: 0.0).times(4.0).toInt().toShort()

    public var message: MessageData? = null // description

    public var cost: Int = 0

    public var time: Float
        get() = if (message == null) 0f else message!!.time
        set(t) {
            if (message != null) {
                message!!.time = t
            }
        }

    public var energy: Float
        get() = if (message == null) 0f else message!!.energy
        set(e) {
            if (message != null) {
                message!!.energy = e
            }
        }

    public fun setAngle(e: Float) {
        if (message != null) {
            message!!.turnangle = e
        }
    }

    public override val idFromPos: Long
        get() = position.toOsmId()

    public override fun calcDistance(p: OsmPos): Int {
        return max(
            1.0, distance(
                this.position, p.position
            ).roundToInt().toDouble()
        ).toInt()
    }

    public var origin: OsmPathElement? = null

    override public fun toString(): String {
        return position.longitude.toIntLongitude()
            .toString() + "_" + position.latitude.toIntLatitude()
    }

    public fun positionEquals(e: OsmPathElement): Boolean {
        return this.position.longitude.toIntLongitude() == e.position.longitude.toIntLongitude() &&
                this.position.latitude.toIntLatitude() == e.position.latitude.toIntLatitude()
    }

    @Throws(IOException::class)
    public fun writeToStream(dos: DataOutput) {
        dos.writeInt(this.position.latitude.toIntLatitude())
        dos.writeInt(this.position.longitude.toIntLongitude())
        dos.writeShort(sElev.toInt())
        dos.writeInt(cost)
    }

    public companion object {
        // construct a path element from a path
        internal fun create(path: OsmPath): OsmPathElement {
            val n = path.targetNode!!
            val pe: OsmPathElement = create(
                n.position.longitude.toIntLongitude(),
                n.position.latitude.toIntLatitude(),
                n.sElev,
                path.originElement
            )
            pe.cost = path.cost
            pe.message = path.message
            return pe
        }

        public fun create(
            ilon: Int,
            ilat: Int,
            selev: Short,
            origin: OsmPathElement?
        ): OsmPathElement {
            val pe = OsmPathElement()
            pe.position = Position(
                ilon.toDoubleLongitude(),
                ilat.toDoubleLatitude(),
                selev.toDouble() / 4.0
            )
            pe.origin = origin
            return pe
        }

        @Throws(IOException::class)
        public fun readFromStream(dis: DataInput): OsmPathElement {
            val pe = OsmPathElement()
            val lat = dis.readInt()
            val lon = dis.readInt()
            val selev = dis.readShort()
            pe.position = Position(
                lon.toDoubleLongitude(),
                lat.toDoubleLatitude(),
                selev.toDouble() / 4.0
            )
            pe.cost = dis.readInt()
            return pe
        }
    }
}
