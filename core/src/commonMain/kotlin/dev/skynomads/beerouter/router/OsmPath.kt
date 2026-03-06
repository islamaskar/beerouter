/**
 * Container for link between two Osm nodes
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.OsmLink
import dev.skynomads.beerouter.mapaccess.OsmLinkHolder
import dev.skynomads.beerouter.mapaccess.OsmNode
import dev.skynomads.beerouter.mapaccess.TurnRestriction.Companion.isTurnForbidden
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import dev.skynomads.beerouter.osm.toSElev
import dev.skynomads.beerouter.util.CheapRuler
import dev.skynomads.beerouter.util.CheapRuler.getLonLatToMeterScales
import kotlin.math.cos
import kotlin.math.sin

public abstract class OsmPath : OsmLinkHolder {
    /**
     * The cost of that path (a modified distance)
     */
    public var cost: Int = 0

    // the elevation assumed for that path can have a value
    // if the corresponding node has not
    public var selev: Short = 0

    public var airdistance: Int = 0 // distance to endpos

    public var sourceNode: OsmNode? = null
    public var targetNode: OsmNode? = null

    public var link: OsmLink? = null
        protected set
    public var originElement: OsmPathElement? = null
    public var myElement: OsmPathElement? = null

    public var treedepth: Int = 0

    // the position of the waypoint just before
    // this path position (for angle calculation)
    public var originLon: Int = 0
    public var originLat: Int = 0

    // the classifier of the segment just before this paths position
    protected var lastClassifier: Float = 0f
    protected var lastInitialCost: Float = 0f

    protected var priorityclassifier: Int = 0

    protected var bitfield: Int = PATH_START_BIT

    private fun getBit(mask: Int): Boolean {
        return (bitfield and mask) != 0
    }

    private fun setBit(mask: Int, bit: Boolean) {
        if (getBit(mask) != bit) {
            bitfield = bitfield xor mask
        }
    }

    public fun didEnterDestinationArea(): Boolean {
        return !getBit(HAD_DESTINATION_START_BIT) && getBit(IS_ON_DESTINATION_BIT)
    }

    public var message: MessageData? = null

    public fun init(link: OsmLink) {
        this.link = link
        targetNode = link.getTarget(null)
        selev = toSElev(targetNode!!.position.altitude)

        originLon = -1
        originLat = -1
    }

    public fun init(
        origin: OsmPath,
        link: OsmLink,
        refTrack: OsmTrack?,
        detailMode: Boolean,
        rc: RoutingContext
    ) {
        if (origin.myElement == null) {
            val n = origin.targetNode!!
            origin.myElement = OsmPathElement.create(
                n.position.longitude.toIntLongitude(),
                n.position.latitude.toIntLatitude(),
                toSElev(n.position.altitude),
                origin.originElement
            ).also {
                it.cost = origin.cost
                it.message = origin.message
            }
        }
        this.originElement = origin.myElement
        this.link = link
        this.sourceNode = origin.targetNode
        this.targetNode = link.getTarget(sourceNode)
        this.cost = origin.cost
        this.lastClassifier = origin.lastClassifier
        this.lastInitialCost = origin.lastInitialCost
        this.bitfield = origin.bitfield
        this.priorityclassifier = origin.priorityclassifier
        init(origin)
        addAddionalPenalty(refTrack, detailMode, origin, link, rc)
    }

    protected abstract fun init(orig: OsmPath?)

    protected abstract fun resetState()

    protected fun addAddionalPenalty(
        refTrack: OsmTrack?,
        detailMode: Boolean,
        origin: OsmPath,
        link: OsmLink,
        rc: RoutingContext
    ) {
        val description = link.descriptionBitmap
        if (description == null) { // could be a beeline path
            message = MessageData().also {
                it.turnangle = 0f
                it.time = 1f
                it.energy = 0f
                it.priorityclassifier = 0
                it.classifiermask = 0
                it.lon = targetNode!!.position.longitude.toIntLongitude()
                it.lat = targetNode!!.position.latitude.toIntLatitude()
                it.ele = Short.MIN_VALUE
                it.linkdist = sourceNode!!.calcDistance(targetNode!!)
                it.wayTags = mapOf("direct_segment" to "$seg")
                seg++
            }
            return
        }

        val recordTransferNodes = detailMode

        rc.nogoCost = 0.0

        // extract the 3 positions of the first section
        var lon0 = origin.originLon
        var lat0 = origin.originLat

        var lon1 = sourceNode!!.position.longitude.toIntLongitude()
        var lat1 = sourceNode!!.position.latitude.toIntLatitude()
        var ele1 = origin.selev

        var linkdisttotal = 0

        message = if (detailMode) MessageData() else null

        val isReverse = link.isReverse(sourceNode)

        // evaluate the way tags
        rc.way.evaluate(rc.inverseDirection xor isReverse, description)

        // and check if is useful
        if (rc.ai != null && rc.ai!!.polygon!!.isWithin(lon1.toLong(), lat1.toLong())) {
            rc.ai!!.checkAreaInfo(rc.way, ele1 / 4.0, description)
        }

        // calculate the costfactor inputs
        val costfactor = rc.way.costfactor
        val isTrafficBackbone = cost == 0 && rc.way.isTrafficBackbone > 0f
        val lastpriorityclassifier = priorityclassifier
        priorityclassifier = rc.way.priorityClassifier.toInt()

        // *** add initial cost if the classifier changed
        val newClassifier = rc.way.initialClassifier
        val newInitialCost = rc.way.initialcost
        val classifierDiff = newClassifier - lastClassifier
        if (newClassifier.toDouble() != 0.0 && lastClassifier.toDouble() != 0.0 && (classifierDiff > 0.0005 || classifierDiff < -0.0005)) {
            val initialcost = if (rc.inverseDirection) lastInitialCost else newInitialCost
            if (initialcost >= 1000000.0) {
                cost = -1
                return
            }

            val iicost = initialcost.toInt()
            if (message != null) {
                message!!.linkinitcost += iicost
            }
            cost += iicost
        }
        lastClassifier = newClassifier
        lastInitialCost = newInitialCost

        // *** destination logic: no destination access in between
        val classifiermask = rc.way.classifierMask.toInt()
        val newDestination = (classifiermask and 64) != 0
        val oldDestination = getBit(IS_ON_DESTINATION_BIT)
        if (getBit(PATH_START_BIT)) {
            setBit(PATH_START_BIT, false)
            setBit(CAN_LEAVE_DESTINATION_BIT, newDestination)
            setBit(HAD_DESTINATION_START_BIT, newDestination)
        } else {
            if (oldDestination && !newDestination) {
                if (getBit(CAN_LEAVE_DESTINATION_BIT)) {
                    setBit(CAN_LEAVE_DESTINATION_BIT, false)
                } else {
                    cost = -1
                    return
                }
            }
        }
        setBit(IS_ON_DESTINATION_BIT, newDestination)


        var transferNode = if (link.geometry == null)
            null
        else
            rc.geometryDecoder.decodeGeometry(link.geometry!!, sourceNode, targetNode!!, isReverse)

        var nsection = 0
        while (true) {
            originLon = lon1
            originLat = lat1

            val lon2: Int
            val lat2: Int
            var ele2: Short
            val originEle2: Short

            if (transferNode == null) {
                lon2 = targetNode!!.position.longitude.toIntLongitude()
                lat2 = targetNode!!.position.latitude.toIntLatitude()
                originEle2 = toSElev(targetNode!!.position.altitude)
            } else {
                lon2 = transferNode.ilon
                lat2 = transferNode.ilat
                originEle2 = transferNode.selev
            }
            ele2 = originEle2

            var isStartpoint = lon0 == -1 && lat0 == -1

            // check turn restrictions (n detail mode (=final pass) no TR to not mess up voice hints)
            if (nsection == 0 && rc.global.considerTurnRestrictions && !detailMode && !isStartpoint) {
                if (if (rc.inverseDirection)
                        isTurnForbidden(
                            sourceNode!!.firstRestriction,
                            lon2,
                            lat2,
                            lon0,
                            lat0,
                            rc.global.bikeMode || rc.global.footMode,
                            rc.global.carMode
                        )
                    else
                        isTurnForbidden(
                            sourceNode!!.firstRestriction,
                            lon0,
                            lat0,
                            lon2,
                            lat2,
                            rc.global.bikeMode || rc.global.footMode,
                            rc.global.carMode
                        )
                ) {
                    cost = -1
                    return
                }
            }

            // if recording, new MessageData for each section (needed for turn-instructions)
            if (message?.wayTags != null) {
                originElement!!.message = message
                message = MessageData()
            }

            var dist = rc.calcDistance(lon1, lat1, lon2, lat2)

            var stopAtEndpoint = false
            if (rc.shortestmatch) {
                if (rc.isEndpoint) {
                    stopAtEndpoint = true
                    ele2 = interpolateEle(ele1, ele2, rc.wayfraction)
                } else {
                    // we just start here, reset everything
                    cost = 0
                    resetState()
                    lon0 = -1 // reset turncost-pipe
                    lat0 = -1
                    isStartpoint = true

                    if (recordTransferNodes) {
                        if (rc.wayfraction > 0.0) {
                            ele1 = interpolateEle(ele1, ele2, 1.0 - rc.wayfraction)
                            originElement = OsmPathElement.create(
                                rc.ilonshortest,
                                rc.ilatshortest,
                                ele1,
                                null
                            )
                        } else {
                            originElement = null // prevent duplicate point
                        }
                    }

                    if (rc.checkPendingEndpoint()) {
                        dist = rc.calcDistance(rc.ilonshortest, rc.ilatshortest, lon2, lat2)
                        if (rc.shortestmatch) {
                            stopAtEndpoint = true
                            ele2 = interpolateEle(ele1, ele2, rc.wayfraction)
                        }
                    }
                }
            }

            message?.let { it.linkdist += dist }
            linkdisttotal += dist

            // apply a start-direction if appropriate (by faking the origin position)
            if (isStartpoint) {
                if (rc.startDirectionValid) {
                    val dir = rc.startDirection!! * CheapRuler.DEG_TO_RAD
                    val lonlat2m = getLonLatToMeterScales((lon0 + lat1) shr 1)
                    lon0 = lon1 - (1000.0 * sin(dir) / lonlat2m!![0]).toInt()
                    lat0 = lat1 - (1000.0 * cos(dir) / lonlat2m[1]).toInt()
                } else {
                    lon0 = lon1 - (lon2 - lon1)
                    lat0 = lat1 - (lat2 - lat1)
                }
            }
            val angle = rc.anglemeter.calcAngle(lon0, lat0, lon1, lat1, lon2, lat2)
            val cosangle = rc.anglemeter.cosAngle

            // *** elevation stuff
            var delta_h = 0.0
            if (ele2 == Short.MIN_VALUE) ele2 = ele1
            if (ele1 != Short.MIN_VALUE) {
                delta_h = (ele2 - ele1) / 4.0
                if (rc.inverseDirection) {
                    delta_h = -delta_h
                }
            }


            val elevation = if (ele2 == Short.MIN_VALUE) 100.0 else ele2 / 4.0

            var sectionCost = processWaySection(
                rc,
                dist.toDouble(),
                delta_h,
                elevation,
                angle,
                cosangle,
                isStartpoint,
                nsection,
                lastpriorityclassifier
            )
            if ((sectionCost < 0.0 || costfactor > 9998.0 && !detailMode) || sectionCost + cost >= 2000000000.0) {
                cost = -1
                return
            }

            if (isTrafficBackbone) {
                sectionCost = 0.0
            }

            cost += sectionCost.toInt()

            // compute kinematic
            computeKinematic(rc, dist.toDouble(), delta_h, detailMode)

            message?.let {
                it.turnangle = angle.toFloat()
                it.time = this.totalTime.toFloat()
                it.energy = this.totalEnergy.toFloat()
                it.priorityclassifier = priorityclassifier
                it.classifiermask = classifiermask
                it.lon = lon2
                it.lat = lat2
                it.ele = originEle2
                it.wayTags = rc.way.getMap(isReverse, description)
            }

            if (stopAtEndpoint) {
                if (recordTransferNodes) {
                    originElement = OsmPathElement.create(
                        rc.ilonshortest,
                        rc.ilatshortest,
                        originEle2,
                        originElement
                    )
                    originElement!!.cost = cost
                    originElement!!.message = message
                }
                cost = if (rc.nogoCost < 0) {
                    -1
                } else {
                    (cost + rc.nogoCost).toInt()
                }
                return
            }

            if (transferNode == null) {
                // *** penalty for being part of the reference track
                if (refTrack != null && refTrack.containsNode(targetNode!!) && refTrack.containsNode(
                        sourceNode!!
                    )
                ) {
                    val reftrackcost = linkdisttotal
                    cost += reftrackcost
                }
                selev = ele2
                break
            }
            transferNode = transferNode.next

            if (recordTransferNodes) {
                originElement =
                    OsmPathElement.create(lon2, lat2, originEle2, originElement)
                originElement!!.cost = cost
            }
            lon0 = lon1
            lat0 = lat1
            lon1 = lon2
            lat1 = lat2
            ele1 = ele2
            nsection++
        }

        // check for nogo-matches (after the *actual* start of segment)
        if (rc.nogoCost < 0) {
            cost = -1
            return
        } else {
            cost = (cost + rc.nogoCost).toInt()
        }

        // add target-node costs
        val targetCost = processTargetNode(rc)
        if (targetCost < 0.0 || targetCost + cost >= 2000000000.0) {
            cost = -1
            return
        }
        cost += targetCost.toInt()
    }


    public fun interpolateEle(e1: Short, e2: Short, fraction: Double): Short {
        if (e1 == Short.MIN_VALUE || e2 == Short.MIN_VALUE) {
            return Short.MIN_VALUE
        }
        return (e1 * (1.0 - fraction) + e2 * fraction).toInt().toShort()
    }

    protected abstract fun processWaySection(
        rc: RoutingContext,
        dist: Double,
        delta_h: Double,
        elevation: Double,
        angle: Double,
        cosangle: Double,
        isStartpoint: Boolean,
        nsection: Int,
        lastpriorityclassifier: Int
    ): Double

    protected abstract fun processTargetNode(rc: RoutingContext): Double

    protected open fun computeKinematic(
        rc: RoutingContext,
        dist: Double,
        delta_h: Double,
        detailMode: Boolean
    ) {
    }

    public abstract fun elevationCorrection(): Int

    public abstract fun definitlyWorseThan(p: OsmPath?): Boolean

    public open val totalTime: Double
        get() = 0.0

    public open val totalEnergy: Double
        get() = 0.0

    public companion object {
        private const val PATH_START_BIT = 1
        private const val CAN_LEAVE_DESTINATION_BIT = 2
        private const val IS_ON_DESTINATION_BIT = 4
        private const val HAD_DESTINATION_START_BIT = 8
        public var seg: Int = 1
    }
}
