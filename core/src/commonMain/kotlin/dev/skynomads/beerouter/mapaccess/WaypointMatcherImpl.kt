package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.WaypointMatcher
import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.osm.osmIdOf
import dev.skynomads.beerouter.osm.positionOf
import dev.skynomads.beerouter.util.CheapAngleMeter.Companion.getDifferenceFromDirection
import dev.skynomads.beerouter.util.CheapAngleMeter.Companion.getDirection
import dev.skynomads.beerouter.util.CheapRuler.getLonLatToMeterScales
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * the WaypointMatcher is feeded by the decoder with geoemtries of ways that are
 * already check for allowed access according to the current routing profile
 *
 *
 * It matches these geometries against the list of waypoints to find the best
 * match for each waypoint
 */
public class WaypointMatcherImpl(
    waypoints: MutableList<MatchedWaypoint>,
    maxDistance: Double,
    islandPairs: OsmNodePairSet
) : WaypointMatcher {
    private val waypoints: MutableList<MatchedWaypoint>
    private val islandPairs: OsmNodePairSet

    private var startPosition: Position = Position(0, 0)
    private var targetPosition: Position = Position(0, 0)
    private var anyUpdate = false
    private var lastPosition: Position = Position(0, 0)
    public var useAsStartWay: Boolean = true
    private val maxWptIdx: Int
    private var maxDistance: Double
    public var useDynamicRange: Boolean = false

    private val comparator: Comparator<MatchedWaypoint> = compareBy({ it.radius }, { it.directionDiff })

    init {
        this.waypoints = waypoints
        this.islandPairs = islandPairs
        var effectiveMaxDistance = maxDistance
        if (effectiveMaxDistance < 0.0) {
            effectiveMaxDistance *= -1.0
            useDynamicRange = true
        }
        this.maxDistance = effectiveMaxDistance

        var last: MatchedWaypoint? = null
        for (mwp in waypoints) {
            mwp.radius = effectiveMaxDistance
            if (last != null && mwp.directionToNext == -1.0) {
                last.directionToNext = getDirection(
                    last.waypoint!!.position,
                    mwp.waypoint!!.position
                )
            }
            last = mwp
        }
        // last point has no angle so we are looking back
        val lastidx = waypoints.size - 2
        if (lastidx < 0) {
            last!!.directionToNext = -1.0
        } else {
            last!!.directionToNext = getDirection(
                last.waypoint!!.position,
                waypoints[lastidx].waypoint!!.position
            )
        }
        maxWptIdx = waypoints.size - 1
    }

    private fun checkSegment(lon1: Int, lat1: Int, lon2: Int, lat2: Int) {
        // todo: bounding-box pre-filter

        val lonlat2m = getLonLatToMeterScales((lat1 + lat2) shr 1)
        val dlon2m = lonlat2m[0]
        val dlat2m = lonlat2m[1]

        val dx = (lon2 - lon1) * dlon2m
        val dy = (lat2 - lat1) * dlat2m
        val d = sqrt(dy * dy + dx * dx)

        if (d == 0.0) return

        //for ( MatchedWaypoint mwp : waypoints )
        for (i in waypoints.indices) {
            if (!useAsStartWay && i == 0) continue
            val mwp = waypoints[i]

            if (mwp.type == MatchedWaypoint.Type.DIRECT &&
                (i == 0 ||
                        waypoints[i - 1].type == MatchedWaypoint.Type.DIRECT)
            ) {
                if (mwp.crosspoint == null) {
                    mwp.crosspoint = OsmNode().also { it.position = mwp.waypoint!!.position }
                    mwp.hasUpdate = true
                    anyUpdate = true
                }
                continue
            }

            val wp = mwp.waypoint!!
            val wpLon = wp.position.longitude
            val wpLat = wp.position.latitude
            val x1 = (lon1 - wpLon) * dlon2m
            val y1 = (lat1 - wpLat) * dlat2m
            val x2 = (lon2 - wpLon) * dlon2m
            val y2 = (lat2 - wpLat) * dlat2m
            val r12 = x1 * x1 + y1 * y1
            val r22 = x2 * x2 + y2 * y2
            var radius = abs(if (r12 < r22) y1 * dx - x1 * dy else y2 * dx - x2 * dy) / d

            if (radius <= mwp.radius) {
                var s1 = x1 * dx + y1 * dy
                var s2 = x2 * dx + y2 * dy

                if (s1 < 0.0) {
                    s1 = -s1
                    s2 = -s2
                }
                if (s2 > 0.0) {
                    radius = sqrt(if (s1 < s2) r12 else r22)

                    if (radius > mwp.radius) {
                        continue
                    }
                }
                // new match for that waypoint
                mwp.radius = radius // shortest distance to way
                mwp.hasUpdate = true
                anyUpdate = true
                // calculate crosspoint
                val cp = mwp.crosspoint ?: OsmNode().also { mwp.crosspoint = it }
                if (s2 < 0.0) {
                    val wayfraction = -s2 / (d * d)
                    val xm = x2 - wayfraction * dx
                    val ym = y2 - wayfraction * dy
                    val newLon = (xm / dlon2m + wpLon).toInt()
                    val newLat = (ym / dlat2m + wpLat).toInt()
                    cp.position = positionOf(newLon, newLat)
                } else if (s1 > s2) {
                    cp.position = positionOf(lon2, lat2)
                } else {
                    cp.position = positionOf(lon1, lat1)
                }
            }
        }
    }

    override public fun start(start: Position, target: Position, useAsStartWay: Boolean): Boolean {
        val ilonStart = start.longitude
        val ilatStart = start.latitude
        val ilonTarget = target.longitude
        val ilatTarget = target.latitude
        if (islandPairs.size != 0) {
            val n1 = osmIdOf(ilonStart, ilatStart)
            val n2 = osmIdOf(ilonTarget, ilatTarget)
            if (islandPairs.hasPair(n1, n2)) {
                return false
            }
        }
        startPosition = start
        lastPosition = start
        targetPosition = target
        anyUpdate = false
        this.useAsStartWay = useAsStartWay
        return true
    }

    override public fun transferNode(position: Position) {
        checkSegment(
            lastPosition.longitude,
            lastPosition.latitude,
            position.longitude,
            position.latitude
        )
        lastPosition = position
    }

    override public fun end() {
        checkSegment(
            lastPosition.longitude,
            lastPosition.latitude,
            targetPosition.longitude,
            targetPosition.latitude
        )
        if (anyUpdate) {
            for (mwp in waypoints) {
                if (mwp.hasUpdate) {
                    var angle = getDirection(
                        startPosition.longitude,
                        startPosition.latitude,
                        targetPosition.longitude,
                        targetPosition.latitude
                    )
                    var diff = getDifferenceFromDirection(mwp.directionToNext, angle)

                    mwp.hasUpdate = false

                    val waypointPos = mwp.waypoint!!.position
                    val crosspointPos = mwp.crosspoint!!.position

                    var mw = MatchedWaypoint()
                    mw.waypoint = OsmNode().also { it.position = waypointPos }
                    mw.crosspoint = OsmNode().also { it.position = crosspointPos }
                    mw.node1 = OsmNode(startPosition)
                    mw.node2 = OsmNode(targetPosition)
                    mw.name = mwp.name + "_w_" + mwp.crosspoint.hashCode()
                    mw.radius = mwp.radius
                    mw.directionDiff = diff
                    mw.directionToNext = mwp.directionToNext

                    updateWayList(mwp.wayNearest, mw)

                    // revers
                    angle = getDirection(
                        targetPosition.longitude,
                        targetPosition.latitude,
                        startPosition.longitude,
                        startPosition.latitude
                    )
                    diff = getDifferenceFromDirection(mwp.directionToNext, angle)
                    mw = MatchedWaypoint()
                    mw.waypoint = OsmNode().also { it.position = waypointPos }
                    mw.crosspoint = OsmNode().also { it.position = crosspointPos }
                    mw.node1 = OsmNode(targetPosition)
                    mw.node2 = OsmNode(startPosition)
                    mw.name = mwp.name + "_w2_" + mwp.crosspoint.hashCode()
                    mw.radius = mwp.radius
                    mw.directionDiff = diff
                    mw.directionToNext = mwp.directionToNext

                    updateWayList(mwp.wayNearest, mw)

                    val way = mwp.wayNearest[0]
                    mwp.crosspoint!!.position = requireNotNull(way.crosspoint).position
                    mwp.node1 = OsmNode(requireNotNull(way.node1).position)
                    mwp.node2 = OsmNode(requireNotNull(way.node2).position)
                    mwp.directionDiff = way.directionDiff
                    mwp.radius = way.radius
                }
            }
        }
    }

    override public fun hasMatch(position: Position): Boolean {
        val lon = position.longitude
        val lat = position.latitude
        for (mwp in waypoints) {
            val wpPos = mwp.waypoint?.position ?: continue
            if (wpPos.longitude == lon && wpPos.latitude == lat &&
                (mwp.radius < this.maxDistance || mwp.crosspoint != null)
            ) {
                return true
            }
        }
        return false
    }

    // check limit of list size (avoid long runs)
    public fun updateWayList(ways: MutableList<MatchedWaypoint>, mw: MatchedWaypoint) {
        ways.add(mw)
        // use only shortest distances by smallest direction difference
        ways.sortWith(comparator)
        if (ways.size > MAX_POINTS) ways.removeAt(MAX_POINTS)
    }


    public companion object {
        private const val MAX_POINTS = 5
    }
}
