package dev.skynomads.beerouter.mapaccess

/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
public open class OsmLink {
    private enum class LinkSide {
        FORWARD,
        REVERSE
    }

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

    private fun sideForOrNull(source: OsmNode?): LinkSide? = when {
        n2 != null && n2 !== source -> LinkSide.FORWARD
        n1 != null && n1 !== source -> LinkSide.REVERSE
        else -> null
    }

    private fun sideFor(source: OsmNode?): LinkSide =
        sideForOrNull(source) ?: throw IllegalArgumentException("internal error: unknown source")

    /**
     * Get the relevant target-node for the given source
     */
    public fun getTarget(source: OsmNode?): OsmNode? {
        return when (sideForOrNull(source)) {
            LinkSide.FORWARD -> n2
            LinkSide.REVERSE -> n1
            null -> n1
        }
    }

    /**
     * Get the relevant next-pointer for the given source
     */
    public fun getNext(source: OsmNode?): OsmLink? {
        return when (sideForOrNull(source)) {
            LinkSide.FORWARD -> next
            LinkSide.REVERSE -> previous
            null -> previous
        }
    }

    /**
     * Reset this link for the given direction
     */
    public fun clear(source: OsmNode?): OsmLink? {
        val nextLink = when (sideFor(source)) {
            LinkSide.FORWARD -> {
                val currentNext = next
                next = null
                n2 = null
                firstlinkholder = null
                currentNext
            }

            LinkSide.REVERSE -> {
                val currentPrevious = previous
                previous = null
                n1 = null
                reverselinkholder = null
                currentPrevious
            }
        }

        if (n1 == null && n2 == null) {
            descriptionBitmap = null
            geometry = null
        }
        return nextLink
    }

    public fun setFirstLinkHolder(holder: OsmLinkHolder?, source: OsmNode?) {
        when (sideFor(source)) {
            LinkSide.FORWARD -> firstlinkholder = holder
            LinkSide.REVERSE -> reverselinkholder = holder
        }
    }

    public fun getFirstLinkHolder(source: OsmNode?): OsmLinkHolder? {
        return when (sideFor(source)) {
            LinkSide.FORWARD -> firstlinkholder
            LinkSide.REVERSE -> reverselinkholder
        }
    }

    public fun isReverse(source: OsmNode?): Boolean {
        return sideForOrNull(source) == LinkSide.REVERSE
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
