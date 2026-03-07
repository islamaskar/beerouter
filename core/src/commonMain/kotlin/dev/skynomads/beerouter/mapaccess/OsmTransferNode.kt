/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.osm.latitudeE6
import dev.skynomads.beerouter.osm.longitudeE6
import dev.skynomads.beerouter.osm.osmPositionOf
import dev.skynomads.beerouter.osm.positionOf

public data class OsmTransferNode(
    public var next: OsmTransferNode? = null,
    public var position: Position = positionOf(0, 0)
) {
    private val sElevation: Short
        get() = position.altitude ?: Short.MIN_VALUE

    @Deprecated("Use position instead")
    public var ilon: Int
        get() = position.longitudeE6
        set(value) {
            position = osmPositionOf(value, position.latitudeE6, sElevation)
        }

    @Deprecated("Use position instead")
    public var ilat: Int
        get() = position.latitudeE6
        set(value) {
            position = osmPositionOf(position.longitudeE6, value, sElevation)
        }

    @Deprecated("Use position instead")
    public var selev: Short
        get() = position.altitude ?: Short.MIN_VALUE
        set(value) {
            position = osmPositionOf(position.longitudeE6, position.latitudeE6, value)
        }
}
