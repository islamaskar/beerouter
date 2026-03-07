package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.MicroCache
import dev.skynomads.beerouter.codec.MicroCache2
import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.osm.latitudeE6
import dev.skynomads.beerouter.osm.longitudeE6
import dev.skynomads.beerouter.osm.osmIdOf
import dev.skynomads.beerouter.osm.osmPositionOf
import dev.skynomads.beerouter.osm.positionOf
import dev.skynomads.beerouter.osm.toOsmId
import dev.skynomads.beerouter.osm.toPosition
import dev.skynomads.beerouter.util.CheapRuler.distance
import dev.skynomads.beerouter.util.IByteArrayUnifier
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Container for an osm node
 *
 * @author ab
 */
public open class OsmNode : OsmLink, OsmPos {
    /**
     * The position
     */
    override var position: Position =
        positionOf(0, 0)

    /**
     * The elevation
     */
    @Deprecated("Use position.altitude instead")
    public val sElev: Short
        get() = position.altitude ?: Short.MIN_VALUE

    /**
     * The node-tags, if any
     */
    public var nodeDescription: ByteArray? = null

    public var firstRestriction: TurnRestriction? = null

    public var visitID: Int = 0

    public fun addTurnRestriction(tr: TurnRestriction) {
        tr.next = firstRestriction
        firstRestriction = tr
    }

    /**
     * The links to other nodes
     */
    public var firstlink: OsmLink? = null

    // Cached id computed from lon/lat — stable because lon/lat never change after construction
    private var _idFromPos: Long = position.toOsmId()

    public constructor()

    public constructor(ilon: Int, ilat: Int) {
        this.position = positionOf(ilon, ilat)
        _idFromPos = (ilon.toLong() shl 32) or (ilat.toLong() and 0xFFFFFFFFL)
    }

    public constructor(id: Long) {
        this.position = id.toPosition()
        _idFromPos = id
    }

    public constructor(position: Position) {
        this.position = position
        _idFromPos = position.toOsmId()
    }


    public fun addLink(link: OsmLink, isReverse: Boolean, tn: OsmNode) {
        require(link !== firstlink) { "UUUUPS" }

        if (isReverse) {
            link.n1 = tn
            link.n2 = this
            link.next = tn.firstlink
            link.previous = firstlink
            tn.firstlink = link
            firstlink = link
        } else {
            link.n1 = this
            link.n2 = tn
            link.next = firstlink
            link.previous = tn.firstlink
            tn.firstlink = link
            firstlink = link
        }
    }

    override fun calcDistance(p: OsmPos): Int {
        return max(
            1,
            distance(position, p.position).roundToInt()
        )
    }

    override fun toString(): String {
        return "n_${position.longitudeE6 - 180000000}_${position.latitudeE6 - 90000000}"
    }

    public fun parseNodeBody(mc: MicroCache, hollowNodes: OsmNodesMap, expCtxWay: IByteArrayUnifier) {
        if (mc is MicroCache2) {
            parseNodeBody2(mc, hollowNodes, expCtxWay)
        } else throw IllegalArgumentException("unknown cache version: " + mc::class.simpleName)
    }

    public fun parseNodeBody2(mc: MicroCache2, hollowNodes: OsmNodesMap, expCtxWay: IByteArrayUnifier) {
        val abUnifier = hollowNodes.byteArrayUnifier

        // read turn restrictions
        while (mc.readBoolean()) {
            addTurnRestriction(
                TurnRestriction.create(
                    exceptions = mc.readShort(),
                    isPositive = mc.readBoolean(),
                    fromLon = mc.readInt(),
                    fromLat = mc.readInt(),
                    toLon = mc.readInt(),
                    toLat = mc.readInt()
                )
            )
        }

        val readSElev = mc.readShort()
        position = osmPositionOf(position.longitude, position.latitude, readSElev)
        val nodeDescSize = mc.readVarLengthUnsigned()
        nodeDescription = if (nodeDescSize == 0) null else mc.readUnified(nodeDescSize, abUnifier)

        // Extract lon/lat once from cached id (avoids repeated double→int conversions in the loop)
        val selfId = _idFromPos
        val selfLon = selfId.longitudeE6
        val selfLat = selfId.latitudeE6

        while (mc.hasMoreData()) {
            // read link data
            val endPointer = mc.endPointer
            val linklon = selfLon + mc.readVarLengthSigned()
            val linklat = selfLat + mc.readVarLengthSigned()
            val sizecode = mc.readVarLengthUnsigned()
            val isReverse = (sizecode and 1) != 0
            var description: ByteArray? = null
            val descSize = sizecode shr 1
            if (descSize > 0) {
                description = mc.readUnified(descSize, expCtxWay)
            }
            val geometry = mc.readDataUntil(endPointer)

            addLink(linklon, linklat, description, geometry, hollowNodes, isReverse)
        }
        hollowNodes.remove(this)
    }

    public fun addLink(
        linklon: Int,
        linklat: Int,
        description: ByteArray?,
        geometry: ByteArray?,
        hollowNodes: OsmNodesMap,
        isReverse: Boolean
    ) {
        val selfId = _idFromPos
        if (linklon == selfId.longitudeE6 && linklat == selfId.latitudeE6) {
            return  // skip self-ref
        }

        var tn: OsmNode? = null // find the target node
        var link: OsmLink? = null

        // ...in our known links
        var l = firstlink
        while (l != null) {
            val t = l.getTarget(this)
            val tId = t!!.idFromPos
            if (tId.longitudeE6 == linklon && tId.latitudeE6 == linklat) {
                tn = t
                if (isReverse || (l.descriptionBitmap == null && !l.isReverse(this))) {
                    link = l // the correct one that needs our data
                    break
                }
            }
            l = l.getNext(this)
        }
        if (tn == null) { // .. not found, then check the hollow nodes
                tn = hollowNodes.get(osmIdOf(linklon, linklat)) // target node
            if (tn == null) { // node not yet known, create a new hollow proxy
                tn = OsmNode(linklon, linklat)
                tn.setHollow()
                hollowNodes.put(tn)
                addLink(
                    tn.also { link = it },
                    isReverse,
                    tn
                ) // technical inheritance: link instance in node
            }
        }
        if (link == null) {
            addLink(OsmLink().also { link = it }, isReverse, tn)
        }
        if (!isReverse) {
            link!!.descriptionBitmap = description
            link.geometry = geometry
        }
    }


    public val isHollow: Boolean
        get() = position.altitude == HOLLOW_SELEV

    public fun setHollow() {
        position = Position(position.longitude, position.latitude, HOLLOW_SELEV)
    }

    override val idFromPos: Long
        get() = _idFromPos

    public fun vanish() {
        if (!this.isHollow) {
            var l = firstlink
            while (l != null) {
                val target = l.getTarget(this)
                val nextLink = l.getNext(this)
                if (!target!!.isHollow) {
                    unlinkLink(l)
                    if (!l.isLinkUnused) {
                        target.unlinkLink(l)
                    }
                }
                l = nextLink
            }
        }
    }

    public fun unlinkLink(link: OsmLink) {
        val n = link.clear(this)

        if (link === firstlink) {
            firstlink = n
            return
        }
        var l = firstlink
        while (l != null) {
            // if ( l.isReverse( this ) )
            if (l.n1 !== this && l.n1 != null) { // isReverse inline
                val nl = l.previous
                if (nl === link) {
                    l.previous = n
                    return
                }
                l = nl
            } else if (l.n2 !== this && l.n2 != null) {
                val nl = l.next
                if (nl === link) {
                    l.next = n
                    return
                }
                l = nl
            } else {
                throw IllegalArgumentException("unlinkLink: unknown source")
            }
        }
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OsmNode) return false
        return other.idFromPos == idFromPos
    }

    override fun hashCode(): Int {
        return _idFromPos.hashCode()
    }

    private companion object {
        private const val HOLLOW_SELEV: Short = -12345
    }

}
