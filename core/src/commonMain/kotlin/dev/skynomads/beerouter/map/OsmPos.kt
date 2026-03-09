/**
 * Interface for a position (OsmNode or OsmPath)
 *
 * @author ab
 */
package dev.skynomads.beerouter.map

import dev.skynomads.beerouter.geo.Position

public interface OsmPos {
    public val position: Position

    public fun calcDistance(p: OsmPos): Int

    public val idFromPos: Long
}
