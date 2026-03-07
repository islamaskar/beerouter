package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.WaypointMatcher
import dev.skynomads.beerouter.geo.Position
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
    private var maxDistance: Double
    public var useDynamicRange: Boolean = false

    private val comparator: Comparator<MatchedWaypoint> = compareBy({ it.radius }, { it.directionDiff })

    init {
        require(waypoints.isNotEmpty()) { "waypoints must not be empty" }
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
                    requireNotNull(last.waypoint).position,
                    requireNotNull(mwp.waypoint).position
                )
            }
            last = mwp
        }
        // last point has no angle so we are looking back
        val lastWaypoint = requireNotNull(last)
        val previousIndex = waypoints.lastIndex - 1
        lastWaypoint.directionToNext = if (previousIndex < 0) {
            -1.0
        } else {
            getDirection(
                requireNotNull(lastWaypoint.waypoint).position,
                requireNotNull(waypoints[previousIndex].waypoint).position
            )
        }
    }

    private fun checkSegment(start: Position, end: Position) {
        // todo: bounding-box pre-filter

        val lonlat2m = getLonLatToMeterScales((start.latitude + end.latitude) shr 1)
        val dlon2m = lonlat2m[0]
        val dlat2m = lonlat2m[1]

        val dx = (end.longitude - start.longitude) * dlon2m
        val dy = (end.latitude - start.latitude) * dlat2m
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
            val waypointPosition = wp.position
            val x1 = (start.longitude - waypointPosition.longitude) * dlon2m
            val y1 = (start.latitude - waypointPosition.latitude) * dlat2m
            val x2 = (end.longitude - waypointPosition.longitude) * dlon2m
            val y2 = (end.latitude - waypointPosition.latitude) * dlat2m
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
                    val newLon = (xm / dlon2m + waypointPosition.longitude).toInt()
                    val newLat = (ym / dlat2m + waypointPosition.latitude).toInt()
                    cp.position = positionOf(newLon, newLat)
                } else if (s1 > s2) {
                    cp.position = end
                } else {
                    cp.position = start
                }
            }
        }
    }

    override public fun start(start: Position, target: Position, useAsStartWay: Boolean): Boolean {
        if (islandPairs.size != 0) {
            if (islandPairs.hasPair(start.id, target.id)) {
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
        checkSegment(lastPosition, position)
        lastPosition = position
    }

    override public fun end() {
        checkSegment(lastPosition, targetPosition)
        if (anyUpdate) {
            for (mwp in waypoints) {
                if (mwp.hasUpdate) {
                    var angle = getDirection(startPosition, targetPosition)
                    var diff = getDifferenceFromDirection(mwp.directionToNext, angle)

                    mwp.hasUpdate = false

                    val waypointPos = requireNotNull(mwp.waypoint).position
                    val crosspointPos = requireNotNull(mwp.crosspoint).position

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
                    angle = getDirection(targetPosition, startPosition)
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
        for (mwp in waypoints) {
            val wpPos = mwp.waypoint?.position ?: continue
            if (wpPos.id == position.id &&
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
