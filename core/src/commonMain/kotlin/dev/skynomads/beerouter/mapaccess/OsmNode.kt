package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.MicroCache
import dev.skynomads.beerouter.codec.MicroCache2
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import dev.skynomads.beerouter.osm.toOsmId
import dev.skynomads.beerouter.util.CheapRuler.distance
import dev.skynomads.beerouter.util.IByteArrayUnifier
import org.maplibre.spatialk.geojson.Position
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
        Position(0.0, 0.0, UNSET_ALTITUDE)

    /**
     * The elevation
     */
    @Deprecated("Use position.altitude instead")
    public val sElev: Short
        get() = position.altitude?.times(4.0)?.toInt()?.toShort() ?: Short.MIN_VALUE

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

    public constructor()

    public constructor(ilon: Int, ilat: Int) {
        this.position = Position(
            ilon.toDoubleLongitude(),
            ilat.toDoubleLatitude(),
            UNSET_ALTITUDE
        )
    }

    public constructor(id: Long) {
        val lon = (id shr 32).toInt()
        val lat = (id and 0xffffffffL).toInt()
        this.position = Position(
            lon.toDoubleLongitude(),
            lat.toDoubleLatitude(),
            UNSET_ALTITUDE
        )
    }

    public constructor(position: Position) {
        this.position = position
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
        return "n_" + (position.longitude.toIntLongitude() - 180000000) + "_" + (position.latitude.toIntLatitude() - 90000000)
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
            val tr = TurnRestriction()
            tr.exceptions = mc.readShort()
            tr.isPositive = mc.readBoolean()
            tr.fromLon = mc.readInt()
            tr.fromLat = mc.readInt()
            tr.toLon = mc.readInt()
            tr.toLat = mc.readInt()
            addTurnRestriction(tr)
        }

        val readSElev = mc.readShort()
        position = Position(position.longitude, position.latitude, readSElev.toDouble() / 4.0)
        val nodeDescSize = mc.readVarLengthUnsigned()
        nodeDescription = if (nodeDescSize == 0) null else mc.readUnified(nodeDescSize, abUnifier)

        while (mc.hasMoreData()) {
            // read link data
            val endPointer = mc.endPointer
            val linklon = position.longitude.toIntLongitude() + mc.readVarLengthSigned()
            val linklat = position.latitude.toIntLatitude() + mc.readVarLengthSigned()
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
        if (linklon == position.longitude.toIntLongitude() && linklat == position.latitude.toIntLatitude()) {
            return  // skip self-ref
        }

        var tn: OsmNode? = null // find the target node
        var link: OsmLink? = null

        // ...in our known links
        var l = firstlink
        while (l != null) {
            val t = l.getTarget(this)
            if (t!!.position.longitude.toIntLongitude() == linklon && t.position.latitude.toIntLatitude() == linklat) {
                tn = t
                if (isReverse || (l.descriptionBitmap == null && !l.isReverse(this))) {
                    link = l // the correct one that needs our data
                    break
                }
            }
            l = l.getNext(this)
        }
        if (tn == null) { // .. not found, then check the hollow nodes
            tn = hollowNodes.get(Position(linklon.toDoubleLongitude(), linklat.toDoubleLatitude(), 0.0)) // target node
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
        get() = position.altitude == HOLLOW_ALTITUDE

    public fun setHollow() {
        position = Position(position.longitude, position.latitude, HOLLOW_ALTITUDE)
    }

    override val idFromPos: Long
        get() = position.toOsmId()

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
        return other.position.longitude.toIntLongitude() == position.longitude.toIntLongitude() &&
               other.position.latitude.toIntLatitude() == position.latitude.toIntLatitude()
    }

    override fun hashCode(): Int {
        return position.toOsmId().hashCode()
    }

    private companion object {
        private const val HOLLOW_SELEV: Short = -12345
        private const val ALTITUDE_SCALE: Double = 4.0
        private val UNSET_ALTITUDE: Double = Short.MIN_VALUE.toDouble() / ALTITUDE_SCALE
        private val HOLLOW_ALTITUDE: Double = HOLLOW_SELEV.toDouble() / ALTITUDE_SCALE
    }

}
