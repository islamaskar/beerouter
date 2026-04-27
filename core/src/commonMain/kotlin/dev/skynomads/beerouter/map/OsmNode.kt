package dev.skynomads.beerouter.map

import dev.skynomads.beerouter.codec.MicroCache
import dev.skynomads.beerouter.codec.MicroCache2
import dev.skynomads.beerouter.geo.CheapRuler.distance
import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.geo.UNSET_ELEVATION
import dev.skynomads.beerouter.geo.latitudeFromId
import dev.skynomads.beerouter.geo.longitudeFromId
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
    override var position: Position = Position.ZERO
        internal set

    override var altitude: Short = UNSET_ELEVATION
        internal set

    /**
     * The node-tags, if any
     */
    public var nodeDescription: ByteArray? = null
        internal set

    public var firstRestriction: TurnRestriction? = null
        internal set

    public var visitID: Int = 0
        internal set

    public fun addTurnRestriction(tr: TurnRestriction) {
        tr.next = firstRestriction
        firstRestriction = tr
    }

    /**
     * The links to other nodes
     */
    public var firstlink: OsmLink? = null
        internal set

    private var hollow: Boolean = false

    // Cached id computed from lon/lat — stable because lon/lat never change after construction
    private var _idFromPos: Long = position.id

    public constructor()

    public constructor(ilon: Int, ilat: Int) {
        position = Position(ilon, ilat)
        _idFromPos = position.id
    }

    public constructor(id: Long) {
        this.position = Position(id.longitudeFromId(), id.latitudeFromId())
        _idFromPos = id
    }

    public constructor(position: Position) {
        this.position = Position(position.longitude, position.latitude)
        this.altitude = position.altitude
        _idFromPos = position.id
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

    override fun distanceTo(p: OsmPos): Int {
        val other = p.position
        return max(
            1,
            distance(position.longitude, position.latitude, other.longitude, other.latitude).roundToInt()
        )
    }

    override fun toString(): String {
        return "n_${position.longitude - 180000000}_${position.latitude - 90000000}"
    }

    public fun parseNodeTags(mc: MicroCache, unifier: IByteArrayUnifier): ByteArray? {
        if (mc !is MicroCache2) throw IllegalArgumentException("unknown cache version: " + mc::class.simpleName)

        // skip turn restrictions
        while (mc.readBoolean()) {
            mc.readShort() // exceptions
            mc.readBoolean() // isPositive
            mc.readInt(); mc.readInt() // from
            mc.readInt(); mc.readInt() // to
        }

        val readSElev = mc.readShort()
        altitude = readSElev
        hollow = false
        val nodeDescSize = mc.readVarLengthUnsigned()
        nodeDescription = if (nodeDescSize == 0) null else mc.readUnified(nodeDescSize, unifier)
        return nodeDescription
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
                    from = Position(mc.readInt(), mc.readInt()),
                    to = Position(mc.readInt(), mc.readInt())
                )
            )
        }

        val readSElev = mc.readShort()
        altitude = readSElev
        hollow = false
        val nodeDescSize = mc.readVarLengthUnsigned()
        nodeDescription = if (nodeDescSize == 0) null else mc.readUnified(nodeDescSize, abUnifier)

        while (mc.hasMoreData()) {
            // read link data
            val endPointer = mc.endPointer
            val targetPosition = Position(
                longitude = position.longitude + mc.readVarLengthSigned(),
                latitude = position.latitude + mc.readVarLengthSigned()
            )
            val sizecode = mc.readVarLengthUnsigned()
            val isReverse = (sizecode and 1) != 0
            var description: ByteArray? = null
            val descSize = sizecode shr 1
            if (descSize > 0) {
                description = mc.readUnified(descSize, expCtxWay)
            }
            val geometry = mc.readDataUntil(endPointer)

            addLink(targetPosition, description, geometry, hollowNodes, isReverse)
        }
        hollowNodes.remove(this)
    }

    public fun addLink(
        targetPosition: Position,
        description: ByteArray?,
        geometry: ByteArray?,
        hollowNodes: OsmNodesMap,
        isReverse: Boolean
    ) {
        if (targetPosition.longitude == position.longitude && targetPosition.latitude == position.latitude) {
            return  // skip self-ref
        }

        var tn: OsmNode? = null // find the target node
        var link: OsmLink? = null

        // ...in our known links
        var l = firstlink
        while (l != null) {
            val t = l.getTarget(this)
            val tId = t!!.idFromPos
            if (tId.longitudeFromId() == targetPosition.longitude && tId.latitudeFromId() == targetPosition.latitude) {
                tn = t
                if (isReverse || (l.descriptionBitmap == null && !l.isReverse(this))) {
                    link = l // the correct one that needs our data
                    break
                }
            }
            l = l.getNext(this)
        }
        if (tn == null) { // .. not found, then check the hollow nodes
            tn = hollowNodes.get(targetPosition.id) // target node
            if (tn == null) { // node not yet known, create a new hollow proxy
                tn = OsmNode(targetPosition)
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
        get() = hollow

    public fun setHollow() {
        hollow = true
    }

    public fun clearHollow() {
        hollow = false
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

}
