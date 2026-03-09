/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.map

import dev.skynomads.beerouter.geo.Position

public data class OsmTransferNode(
    public var next: OsmTransferNode? = null,
    public var position: Position = Position.ZERO
)
