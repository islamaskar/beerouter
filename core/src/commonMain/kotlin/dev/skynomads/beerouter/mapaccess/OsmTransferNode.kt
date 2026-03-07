/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.osm.positionOf

public data class OsmTransferNode(
    public var next: OsmTransferNode? = null,
    public var position: Position = positionOf(0, 0)
)
