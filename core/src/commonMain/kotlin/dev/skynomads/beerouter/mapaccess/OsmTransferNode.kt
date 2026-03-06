/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess


public class OsmTransferNode {
    @JvmField
    public var next: OsmTransferNode? = null

    @JvmField
    public var ilon: Int = 0

    @JvmField
    public var ilat: Int = 0

    @JvmField
    public var selev: Short = 0
}
