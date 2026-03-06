/**
 * Information on matched way point
 *
 * @author ab
 */
package dev.skynomads.beerouter.router


public class MessageData : Cloneable {
    public var linkdist: Int = 0
    public var linkelevationcost: Int = 0
    public var linkturncost: Int = 0
    public var linknodecost: Int = 0
    public var linkinitcost: Int = 0

    public var costfactor: Float = 0f
    public var priorityclassifier: Int = 0
    public var classifiermask: Int = 0
    public var turnangle: Float = 0f
    public var wayTags: Map<String, String>? = null
    public var nodeTags: Map<String, String>? = null

    public var lon: Int = 0
    public var lat: Int = 0
    public var ele: Short = 0

    public var time: Float = 0f
    public var energy: Float = 0f

    // speed profile
    public var vmaxExplicit: Int = -1
    public var vmax: Int = -1
    public var vmin: Int = -1
    public var vnode0: Int = 999
    public var vnode1: Int = 999
    public var extraTime: Int = 0

    public fun add(d: MessageData) {
        linkdist += d.linkdist
        linkelevationcost += d.linkelevationcost
        linkturncost += d.linkturncost
        linknodecost += d.linknodecost
        linkinitcost += d.linkinitcost
    }

    public fun copy(): MessageData? {
        return try {
            clone() as MessageData?
        } catch (e: CloneNotSupportedException) {
            throw RuntimeException(e)
        }
    }

    override fun toString(): String {
        return "dist=" + linkdist + " prio=" + this.priorityclassifier + " turn=" + turnangle
    }

    public val isBadOneway: Boolean
        get() = (classifiermask and 1) != 0

    public val isGoodOneway: Boolean
        get() = (classifiermask and 2) != 0

    public val isRoundabout: Boolean
        get() = (classifiermask and 4) != 0

    public val isLinktType: Boolean
        get() = (classifiermask and 8) != 0

    public val isGoodForCars: Boolean
        get() = (classifiermask and 16) != 0
}
