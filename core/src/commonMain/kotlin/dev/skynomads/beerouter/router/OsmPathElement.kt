package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.CheapRuler.distance
import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.OsmPos
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
public class OsmPathElement protected constructor() : OsmPos {
    public override var position: Position = Position.ZERO

    public override val altitude: Short
        get() = position.altitude

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

    public override fun distanceTo(p: OsmPos): Int {
        return max(1, distance(this.position, p.position).roundToInt())
    }

    public var origin: OsmPathElement? = null

    override fun toString(): String =
        "${position.longitude}_${position.latitude}"

    public fun positionEquals(e: OsmPathElement): Boolean {
        return _idFromPos == e._idFromPos
    }

    public fun writeToStream(sink: Sink) {
        sink.writeInt(position.latitude)
        sink.writeInt(position.longitude)
        sink.writeShort(position.altitude)
        sink.writeInt(cost)
    }

    public companion object {
        public fun create(position: Position, origin: OsmPathElement?): OsmPathElement =
            OsmPathElement().apply {
                this.position = position
                _idFromPos = position.id
                this.origin = origin
            }

        public fun create(
            ilon: Int,
            ilat: Int,
            selev: Short,
            origin: OsmPathElement?
        ): OsmPathElement = create(Position(ilon, ilat, selev), origin)

        public fun readFromStream(source: Source): OsmPathElement {
            val lat = source.readInt()
            val lon = source.readInt()
            val encodedAltitude = source.readShort()
            val position = Position(lon, lat, encodedAltitude)
            return OsmPathElement().apply {
                this.position = position
                _idFromPos = position.id
                cost = source.readInt()
            }
        }
    }
}
