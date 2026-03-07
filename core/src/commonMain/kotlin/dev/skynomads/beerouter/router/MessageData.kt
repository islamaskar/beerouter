/**
 * Information on matched way point
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.osm.latitudeE6
import dev.skynomads.beerouter.osm.longitudeE6
import dev.skynomads.beerouter.osm.osmPositionOf
import dev.skynomads.beerouter.osm.positionOf

public data class MessageData(
    public var linkdist: Int = 0,
    public var linkelevationcost: Int = 0,
    public var linkturncost: Int = 0,
    public var linknodecost: Int = 0,
    public var linkinitcost: Int = 0,
    public var costfactor: Float = 0f,
    public var priorityclassifier: Int = 0,
    public var classifiermask: Int = 0,
    public var turnangle: Float = 0f,
    public var wayTags: Map<String, String>? = null,
    public var nodeTags: Map<String, String>? = null,
    public var position: Position = positionOf(0, 0),
    public var time: Float = 0f,
    public var energy: Float = 0f,
    public var vmaxExplicit: Int = -1,
    public var vmax: Int = -1,
    public var vmin: Int = -1,
    public var vnode0: Int = 999,
    public var vnode1: Int = 999,
    public var extraTime: Int = 0
) {
    public enum class ClassifierFlag(public val bit: Int) {
        BAD_ONEWAY(1),
        GOOD_ONEWAY(2),
        ROUNDABOUT(4),
        LINK_TYPE(8),
        GOOD_FOR_CARS(16)
    }

    public fun add(d: MessageData) {
        linkdist += d.linkdist
        linkelevationcost += d.linkelevationcost
        linkturncost += d.linkturncost
        linknodecost += d.linknodecost
        linkinitcost += d.linkinitcost
    }

    override fun toString(): String = "dist=$linkdist prio=$priorityclassifier turn=$turnangle"

    private fun hasFlag(flag: ClassifierFlag): Boolean = (classifiermask and flag.bit) != 0

    public val isBadOneway: Boolean
        get() = hasFlag(ClassifierFlag.BAD_ONEWAY)

    public val isGoodOneway: Boolean
        get() = hasFlag(ClassifierFlag.GOOD_ONEWAY)

    public val isRoundabout: Boolean
        get() = hasFlag(ClassifierFlag.ROUNDABOUT)

    public val isLinkType: Boolean
        get() = hasFlag(ClassifierFlag.LINK_TYPE)

    public val isGoodForCars: Boolean
        get() = hasFlag(ClassifierFlag.GOOD_FOR_CARS)

    @Deprecated("Use position.longitude instead")
    public var lon: Int
        get() = position.longitudeE6
        set(value) {
            position = osmPositionOf(value, position.latitudeE6, position.altitude)
        }

    @Deprecated("Use position.latitude instead")
    public var lat: Int
        get() = position.latitudeE6
        set(value) {
            position = osmPositionOf(position.longitudeE6, value, position.altitude)
        }

    @Deprecated("Use position.altitude instead")
    public var ele: Short
        get() = position.altitude ?: Short.MIN_VALUE
        set(value) {
            position = position.withAltitude(value.takeUnless { it == Short.MIN_VALUE })
        }
}
