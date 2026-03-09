package dev.skynomads.beerouter.mapcreator

public class OsmNodePT() : OsmNodeP() {
    public var descriptionBits: ByteArray? = null
    public var firstRestrictionData: RestrictionData? = null

    public constructor(node: OsmNodeP) : this() {
        ilat = node.ilat
        ilon = node.ilon
        selev = node.selev
        bits = node.bits
    }

    public constructor(descriptionBits: ByteArray?) : this() {
        this.descriptionBits = descriptionBits
    }

    override fun getNodeDecsription(): ByteArray? = descriptionBits
    override fun getFirstRestriction(): RestrictionData? = firstRestrictionData
    override fun isTransferNode(): Boolean = false
}
