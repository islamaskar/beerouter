package dev.skynomads.beerouter.mapaccess

/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
public open class OsmLink {
    /**
     * The description bitmap contains the waytags (valid for both directions)
     */
    public var descriptionBitmap: ByteArray? = null

    /**
     * The geometry contains intermediate nodes, null for none (valid for both directions)
     */
    public var geometry: ByteArray? = null

    // a link logically knows only its target, but for the reverse link, source and target are swapped
    public var n1: OsmNode? = null
    public var n2: OsmNode? = null

    // same for the next-link-for-node pointer: previous applies to the reverse link
    public var previous: OsmLink? = null
    public var next: OsmLink? = null

    private var reverselinkholder: OsmLinkHolder? = null
    private var firstlinkholder: OsmLinkHolder? = null

    public constructor()

    public constructor(source: OsmNode?, target: OsmNode?) {
        n1 = source
        n2 = target
    }

    /**
     * Get the relevant target-node for the given source
     */
    public fun getTarget(source: OsmNode?): OsmNode? {
        return if (n2 !== source && n2 != null) n2 else n1
    }

    /**
     * Get the relevant next-pointer for the given source
     */
    public fun getNext(source: OsmNode?): OsmLink? {
        return if (n2 !== source && n2 != null) next else previous
    }

    /**
     * Reset this link for the given direction
     */
    public fun clear(source: OsmNode?): OsmLink? {
        val n: OsmLink?
        if (n2 != null && n2 !== source) {
            n = next
            next = null
            n2 = null
            firstlinkholder = null
        } else if (n1 != null && n1 !== source) {
            n = previous
            previous = null
            n1 = null
            reverselinkholder = null
        } else {
            throw IllegalArgumentException("internal error: setNext: unknown source")
        }
        if (n1 == null && n2 == null) {
            descriptionBitmap = null
            geometry = null
        }
        return n
    }

    public fun setFirstLinkHolder(holder: OsmLinkHolder?, source: OsmNode?) {
        if (n2 != null && n2 !== source) {
            firstlinkholder = holder
        } else if (n1 != null && n1 !== source) {
            reverselinkholder = holder
        } else {
            throw IllegalArgumentException("internal error: setFirstLinkHolder: unknown source")
        }
    }

    public fun getFirstLinkHolder(source: OsmNode?): OsmLinkHolder? {
        return if (n2 != null && n2 !== source) {
            firstlinkholder
        } else if (n1 != null && n1 !== source) {
            reverselinkholder
        } else {
            throw IllegalArgumentException("internal error: getFirstLinkHolder: unknown source")
        }
    }

    public fun isReverse(source: OsmNode?): Boolean {
        return n1 !== source && n1 != null
    }

    public val isBidirectional: Boolean
        get() = n1 != null && n2 != null

    public val isLinkUnused: Boolean
        get() = n1 == null && n2 == null

    public fun addLinkHolder(holder: OsmLinkHolder, source: OsmNode?) {
        val firstHolder = getFirstLinkHolder(source)
        if (firstHolder != null) {
            holder.nextForLink = firstHolder
        }
        setFirstLinkHolder(holder, source)
    }
}
