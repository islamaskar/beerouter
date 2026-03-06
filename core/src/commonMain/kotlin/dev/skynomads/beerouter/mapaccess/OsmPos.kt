/**
 * Interface for a position (OsmNode or OsmPath)
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import org.maplibre.spatialk.geojson.Position


public interface OsmPos {
    public val position: Position

    public fun calcDistance(p: OsmPos): Int

    public val idFromPos: Long

    @Deprecated("use position instead")
    public val iLat: Int
        get() = position.latitude.toIntLatitude()

    @Deprecated("use position instead")
    public val iLon: Int
        get() = position.longitude.toIntLongitude()
}
