package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.OsmPos
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import dev.skynomads.beerouter.osm.toOsmId
import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import dev.skynomads.beerouter.util.CheapRuler.distance
import kotlinx.io.Sink
import kotlinx.io.Source
import org.maplibre.spatialk.geojson.Position
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
        get() = position.altitude?.times(4.0)?.toInt()?.toShort() ?: Short.MIN_VALUE

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

    public fun writeToStream(sink: Sink) {
        sink.writeInt(this.position.latitude.toIntLatitude())
        sink.writeInt(this.position.longitude.toIntLongitude())
        sink.writeShort(toSElev(position.altitude))
        sink.writeInt(cost)
    }

    private fun toSElev(altitude: Double?): Short =
        altitude?.times(4.0)?.toInt()?.toShort() ?: Short.MIN_VALUE

    public companion object {
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

        public fun readFromStream(source: Source): OsmPathElement {
            val pe = OsmPathElement()
            val lat = source.readInt()
            val lon = source.readInt()
            val selev = source.readShort()
            pe.position = Position(
                lon.toDoubleLongitude(),
                lat.toDoubleLatitude(),
                selev.toDouble() / 4.0
            )
            pe.cost = source.readInt()
            return pe
        }
    }
}
