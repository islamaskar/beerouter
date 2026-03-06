/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

public data class OsmTransferNode(
    public var next: OsmTransferNode? = null,
    public var ilon: Int = 0,
    public var ilat: Int = 0,
    public var selev: Short = 0
)
