package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.OsmPos
import dev.skynomads.beerouter.osm.latitudeE6
import dev.skynomads.beerouter.osm.longitudeE6
import dev.skynomads.beerouter.osm.osmPositionOf
import dev.skynomads.beerouter.osm.positionOf
import dev.skynomads.beerouter.osm.toSElev
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
    public override var position: Position = positionOf(0, 0, 0.0)

    @Deprecated("Use position.altitude instead")
    public val sElev: Short
        get() = position.altitude?.times(4.0)?.toInt()?.toShort() ?: Short.MIN_VALUE

    public var message: MessageData? = null // description

    public var cost: Int = 0

    public var time: Float
        get() = message?.time ?: 0f
        set(t) {
            message?.time = t
        }

    public var energy: Float
        get() = message?.energy ?: 0f
        set(e) {
            message?.energy = e
        }

    public fun setAngle(e: Float) {
        message?.turnangle = e
    }

    // Cached id — stable because lon/lat never change after creation
    private var _idFromPos: Long = 0L

    public override val idFromPos: Long
        get() = _idFromPos

    public override fun calcDistance(p: OsmPos): Int {
        return max(1, distance(this.position, p.position).roundToInt())
    }

    public var origin: OsmPathElement? = null

    override fun toString(): String =
        "${position.longitudeE6}_${position.latitudeE6}"

    public fun positionEquals(e: OsmPathElement): Boolean {
        return _idFromPos == e._idFromPos
    }

    public fun writeToStream(sink: Sink) {
        sink.writeInt(position.latitudeE6)
        sink.writeInt(position.longitudeE6)
        sink.writeShort(toSElev(position.altitude))
        sink.writeInt(cost)
    }

    public companion object {
        public fun create(position: Position, origin: OsmPathElement?): OsmPathElement {
            val pe = OsmPathElement()
            pe.position = position
            pe._idFromPos = position.longitudeE6.toLong() shl 32 or position.latitudeE6.toLong()
            pe.origin = origin
            return pe
        }

        public fun create(
            ilon: Int,
            ilat: Int,
            selev: Short,
            origin: OsmPathElement?
        ): OsmPathElement = create(osmPositionOf(ilon, ilat, selev), origin)

        public fun readFromStream(source: Source): OsmPathElement {
            val pe = OsmPathElement()
            val lat = source.readInt()
            val lon = source.readInt()
            val selev = source.readShort()
            pe.position = osmPositionOf(lon, lat, selev)
            pe._idFromPos = (lon.toLong() shl 32) or lat.toLong()
            pe.cost = source.readInt()
            return pe
        }
    }
}
