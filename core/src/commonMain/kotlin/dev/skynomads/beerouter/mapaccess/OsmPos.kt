/**
 * Interface for a position (OsmNode or OsmPath)
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.osm.latitudeE6
import dev.skynomads.beerouter.osm.longitudeE6


public interface OsmPos {
    public val position: Position

    public fun calcDistance(p: OsmPos): Int

    public val idFromPos: Long

    @Deprecated("use position instead")
    public val iLat: Int
        get() = position.latitudeE6

    @Deprecated("use position instead")
    public val iLon: Int
        get() = position.longitudeE6
}
