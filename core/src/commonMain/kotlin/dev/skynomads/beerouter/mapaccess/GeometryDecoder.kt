/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.osm.toSElev
import dev.skynomads.beerouter.util.ByteDataReader

public class GeometryDecoder {
    private val r = ByteDataReader()
    private val cachedNodes: Array<OsmTransferNode> = Array(CACHED_NODE_COUNT) { OsmTransferNode() }

    // result-cache
    private var firstTransferNode: OsmTransferNode? = null
    private var lastReverse = false
    private var lastGeometry: ByteArray? = null

    public fun decodeGeometry(
        geometry: ByteArray,
        sourceNode: OsmNode?,
        targetNode: OsmNode,
        reverseLink: Boolean
    ): OsmTransferNode? {
        if (lastGeometry === geometry && lastReverse == reverseLink) {
            return firstTransferNode
        }

        firstTransferNode = null
        var lastTransferNode: OsmTransferNode? = null
        val startnode: OsmNode = (if (reverseLink) targetNode else sourceNode)!!
        r.reset(geometry)
        val startnodeId = startnode.idFromPos
        var olon = (startnodeId shr 32).toInt()
        var olat = (startnodeId and 0xffffffffL).toInt()
        var oselev = toSElev(startnode.position.altitude).toInt()
        var idx = 0
        while (r.hasMoreData()) {
            val trans = cachedNodes.getOrNull(idx++) ?: OsmTransferNode()
            trans.ilon = olon + r.readVarLengthSigned()
            trans.ilat = olat + r.readVarLengthSigned()
            trans.selev = (oselev + r.readVarLengthSigned()).toShort()
            olon = trans.ilon
            olat = trans.ilat
            oselev = trans.selev.toInt()
            if (reverseLink) { // reverse chaining
                trans.next = firstTransferNode
                firstTransferNode = trans
            } else {
                trans.next = null
                if (lastTransferNode == null) {
                    firstTransferNode = trans
                } else {
                    lastTransferNode.next = trans
                }
                lastTransferNode = trans
            }
        }

        lastReverse = reverseLink
        lastGeometry = geometry

        return firstTransferNode
    }

    private companion object {
        private const val CACHED_NODE_COUNT = 128
    }
}
