/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.mapaccess

import androidx.collection.MutableLongObjectMap
import dev.skynomads.beerouter.osm.toOsmId
import dev.skynomads.beerouter.util.ByteArrayUnifier
import org.maplibre.spatialk.geojson.Position

public class OsmNodesMap {
    private val hmap: MutableLongObjectMap<OsmNode> = MutableLongObjectMap(4096)

    public val byteArrayUnifier: ByteArrayUnifier = ByteArrayUnifier(16384, false)

    @JvmField
    public var nodesCreated: Int = 0
    public var maxmem: Long = 0
    private var currentmaxmem: Long = 4000000 // start with 4 MB
    public var lastVisitID: Int = 1000
    public var baseID: Int = 1000

    @JvmField
    public var destination: OsmNode? = null

    @JvmField
    public var currentPathCost: Int = 0

    @JvmField
    public var currentMaxCost: Int = 1000000000

    @JvmField
    public var endNode1: OsmNode? = null

    @JvmField
    public var endNode2: OsmNode? = null

    @JvmField
    public var cleanupMode: Int = 0

    public fun cleanupAndCount(nodes: List<OsmNode>) {
        if (cleanupMode == 0) {
            justCount(nodes)
        } else {
            cleanupPeninsulas(nodes)
        }
    }

    private fun justCount(nodes: List<OsmNode>) {
        for (i in nodes.indices) {
            val n = nodes[i]
            if (n.firstlink != null) {
                nodesCreated++
            }
        }
    }

    private fun cleanupPeninsulas(nodes: List<OsmNode>) {
        baseID = lastVisitID++
        for (i in nodes.indices) { // loop over nodes again just for housekeeping
            val n = nodes[i]
            if (n.firstlink != null) {
                if (n.visitID == 1) {
                    try {
                        minVisitIdInSubtree(null, n)
                    } catch (soe: StackOverflowError) {
                        // System.out.println( "+++++++++++++++ StackOverflowError ++++++++++++++++" );
                    }
                }
            }
        }
    }

    private fun minVisitIdInSubtree(source: OsmNode?, n: OsmNode): Int {
        if (n.visitID == 1) n.visitID = baseID // border node
        else n.visitID = lastVisitID++
        var minId = n.visitID
        nodesCreated++

        var nextLink: OsmLink?
        var l = n.firstlink
        while (l != null) {
            nextLink = l.getNext(n)

            val t = l.getTarget(n)
            if (t === source) {
                l = nextLink
                continue
            }
            if (t!!.isHollow) {
                l = nextLink
                continue
            }

            var minIdSub = t.visitID
            if (minIdSub == 1) {
                minIdSub = baseID
            } else if (minIdSub == 0) {
                val nodesCreatedUntilHere = nodesCreated
                minIdSub = minVisitIdInSubtree(n, t)
                if (minIdSub > n.visitID) { // peninsula ?
                    nodesCreated = nodesCreatedUntilHere
                    n.unlinkLink(l)
                    t.unlinkLink(l)
                }
            } else if (minIdSub < baseID) {
                l = nextLink
                continue
            } else if (cleanupMode == 2) {
                minIdSub = baseID // in tree-mode, hitting anything is like a gateway
            }
            if (minIdSub < minId) minId = minIdSub
            l = nextLink
        }
        return minId
    }


    public fun isInMemoryBounds(npaths: Int, extend: Boolean): Boolean {
        //    long total = nodesCreated * 76L + linksCreated * 48L;
        var total = nodesCreated * 95L + npaths * 200L

        if (extend) {
            total += 100000

            // when extending, try to have 1 MB  space
            val delta = total + 1900000 - currentmaxmem
            if (delta > 0) {
                currentmaxmem += delta
                if (currentmaxmem > maxmem) {
                    currentmaxmem = maxmem
                }
            }
        }
        return total <= currentmaxmem
    }

    private var nodes2check: MutableList<OsmNode>? = null

    // is there an escape from this node
    // to a hollow node (or destination node) ?
    public fun canEscape(n0: OsmNode?): Boolean {
        var sawLowIDs = false
        lastVisitID++
        nodes2check!!.clear()
        nodes2check!!.add(n0!!)
        while (!nodes2check!!.isEmpty()) {
            val n = nodes2check!!.removeAt(nodes2check!!.size - 1)
            if (n.visitID < baseID) {
                n.visitID = lastVisitID
                nodesCreated++
                var l = n.firstlink
                while (l != null) {
                    val t = l.getTarget(n)
                    nodes2check!!.add(t!!)
                    l = l.getNext(n)
                }
            } else if (n.visitID < lastVisitID) {
                sawLowIDs = true
            }
        }
        if (sawLowIDs) {
            return true
        }

        nodes2check!!.add(n0)
        while (!nodes2check!!.isEmpty()) {
            val n = nodes2check!!.removeAt(nodes2check!!.size - 1)
            if (n.visitID == lastVisitID) {
                n.visitID = lastVisitID
                nodesCreated--
                var l = n.firstlink
                while (l != null) {
                    val t = l.getTarget(n)
                    nodes2check!!.add(t!!)
                    l = l.getNext(n)
                }
                n.vanish()
            }
        }

        return false
    }

    private fun addActiveNode(nodes2check: MutableList<OsmNode>, n: OsmNode) {
        n.visitID = lastVisitID
        nodesCreated++
        nodes2check.add(n)
    }

    public fun clearTemp() {
        nodes2check = null
    }

    public fun collectOutreachers() {
        nodes2check = ArrayList(nodesCreated)
        nodesCreated = 0
        hmap.forEach { _, node ->
            addActiveNode(nodes2check!!, node)
        }

        lastVisitID++
        baseID = lastVisitID

        while (!nodes2check!!.isEmpty()) {
            val n = nodes2check!!.removeAt(nodes2check!!.size - 1)
            n.visitID = lastVisitID

            var l = n.firstlink
            while (l != null) {
                val t = l.getTarget(n)
                if (t!!.visitID != lastVisitID) {
                    addActiveNode(nodes2check!!, t)
                }
                l = l.getNext(n)
            }
            if (destination != null && currentMaxCost < 1000000000) {
                val distance = n.calcDistance(destination!!)
                if (distance > currentMaxCost - currentPathCost + 100) {
                    n.vanish()
                }
            }
            if (n.firstlink == null) {
                nodesCreated--
            }
        }
    }


    /**
     * Get a node from the map
     *
     * @return the node for the given id if exist, else null
     */
    @Deprecated("use get(position: Position)")
    public fun get(ilon: Int, ilat: Int): OsmNode? {
        val id = (ilon.toLong()) shl 32 or ilat.toLong()
        return hmap[id]
    }

    public fun get(position: Position): OsmNode? {
        return hmap[position.toOsmId()]
    }


    public fun remove(node: OsmNode) {
        if (node !== endNode1 && node !== endNode2) { // keep endnodes in hollow-map even when loaded
            hmap.remove(node.idFromPos)
        }
    }

    /**
     * Put a node into the map
     *
     * @return the previous node if that id existed, else null
     */
    public fun put(node: OsmNode): OsmNode? {
        return hmap.put(node.idFromPos, node)
    }
}
