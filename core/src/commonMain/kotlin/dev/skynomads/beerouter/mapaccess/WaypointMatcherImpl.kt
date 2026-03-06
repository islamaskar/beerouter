package dev.skynomads.beerouter.mapaccess

import dev.skynomads.beerouter.codec.WaypointMatcher
import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import dev.skynomads.beerouter.util.CheapAngleMeter.Companion.getDifferenceFromDirection
import dev.skynomads.beerouter.util.CheapAngleMeter.Companion.getDirection
import dev.skynomads.beerouter.util.CheapRuler.getLonLatToMeterScales
import org.maplibre.spatialk.geojson.Position
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

    private var lonStart = 0
    private var latStart = 0
    private var lonTarget = 0
    private var latTarget = 0
    private var anyUpdate = false
    private var lonLast = 0
    private var latLast = 0
    public var useAsStartWay: Boolean = true
    private val maxWptIdx: Int
    private var maxDistance: Double
    public var useDynamicRange: Boolean = false

    private val comparator: Comparator<MatchedWaypoint>?

    init {
        var maxDistance = maxDistance
        this.waypoints = waypoints
        this.islandPairs = islandPairs
        var last: MatchedWaypoint? = null
        this.maxDistance = maxDistance
        if (maxDistance < 0.0) {
            this.maxDistance *= -1.0
            maxDistance *= -1.0
            useDynamicRange = true
        }

        for (mwp in waypoints) {
            mwp.radius = maxDistance
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

        // sort result list
        comparator = object : Comparator<MatchedWaypoint> {
            override fun compare(mw1: MatchedWaypoint, mw2: MatchedWaypoint): Int {
                val cmpDist = mw1.radius.compareTo(mw2.radius)
                if (cmpDist != 0) return cmpDist
                return mw1.directionDiff.compareTo(mw2.directionDiff)
            }
        }
    }

    private fun checkSegment(lon1: Int, lat1: Int, lon2: Int, lat2: Int) {
        // todo: bounding-box pre-filter

        val lonlat2m = getLonLatToMeterScales((lat1 + lat2) shr 1)!!
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
                    mwp.crosspoint = OsmNode()
                    mwp.crosspoint!!.position = mwp.waypoint!!.position
                    mwp.hasUpdate = true
                    anyUpdate = true
                }
                continue
            }

            val wp = mwp.waypoint!!
            val wpLon = wp.position.longitude.toIntLongitude()
            val wpLat = wp.position.latitude.toIntLatitude()
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
                if (mwp.crosspoint == null) mwp.crosspoint = OsmNode()
                if (s2 < 0.0) {
                    val wayfraction = -s2 / (d * d)
                    val xm = x2 - wayfraction * dx
                    val ym = y2 - wayfraction * dy
                    val newLon = (xm / dlon2m + wpLon).toInt()
                    val newLat = (ym / dlat2m + wpLat).toInt()
                    mwp.crosspoint!!.position = Position(
                        newLon.toDoubleLongitude(),
                        newLat.toDoubleLatitude(),
                        0.0
                    )
                } else if (s1 > s2) {
                    mwp.crosspoint!!.position = Position(
                        lon2.toDoubleLongitude(),
                        lat2.toDoubleLatitude(),
                        0.0
                    )
                } else {
                    mwp.crosspoint!!.position = Position(
                        lon1.toDoubleLongitude(),
                        lat1.toDoubleLatitude(),
                        0.0
                    )
                }
            }
        }
    }

    override public fun start(
        ilonStart: Int,
        ilatStart: Int,
        ilonTarget: Int,
        ilatTarget: Int,
        useAsStartWay: Boolean
    ): Boolean {
        if (islandPairs.size() > 0) {
            val n1 = (ilonStart.toLong()) shl 32 or ilatStart.toLong()
            val n2 = (ilonTarget.toLong()) shl 32 or ilatTarget.toLong()
            if (islandPairs.hasPair(n1, n2)) {
                return false
            }
        }
        lonStart = ilonStart
        lonLast = lonStart
        latStart = ilatStart
        latLast = latStart
        lonTarget = ilonTarget
        latTarget = ilatTarget
        anyUpdate = false
        this.useAsStartWay = useAsStartWay
        return true
    }

    override public fun transferNode(ilon: Int, ilat: Int) {
        checkSegment(lonLast, latLast, ilon, ilat)
        lonLast = ilon
        latLast = ilat
    }

    override public fun end() {
        checkSegment(lonLast, latLast, lonTarget, latTarget)
        if (anyUpdate) {
            for (mwp in waypoints) {
                if (mwp.hasUpdate) {
                    var angle = getDirection(lonStart, latStart, lonTarget, latTarget)
                    var diff = getDifferenceFromDirection(mwp.directionToNext, angle)

                    mwp.hasUpdate = false

                    var mw = MatchedWaypoint()
                    mw.waypoint = OsmNode()
                    mw.waypoint!!.position = mwp.waypoint!!.position
                    mw.crosspoint = OsmNode()
                    mw.crosspoint!!.position = mwp.crosspoint!!.position
                    mw.node1 = OsmNode(lonStart, latStart)
                    mw.node2 = OsmNode(lonTarget, latTarget)
                    mw.name = mwp.name + "_w_" + mwp.crosspoint.hashCode()
                    mw.radius = mwp.radius
                    mw.directionDiff = diff
                    mw.directionToNext = mwp.directionToNext

                    updateWayList(mwp.wayNearest, mw)

                    // revers
                    angle = getDirection(lonTarget, latTarget, lonStart, latStart)
                    diff = getDifferenceFromDirection(mwp.directionToNext, angle)
                    mw = MatchedWaypoint()
                    mw.waypoint = OsmNode()
                    mw.waypoint!!.position = mwp.waypoint!!.position
                    mw.crosspoint = OsmNode()
                    mw.crosspoint!!.position = mwp.crosspoint!!.position
                    mw.node1 = OsmNode(lonTarget, latTarget)
                    mw.node2 = OsmNode(lonStart, latStart)
                    mw.name = mwp.name + "_w2_" + mwp.crosspoint.hashCode()
                    mw.radius = mwp.radius
                    mw.directionDiff = diff
                    mw.directionToNext = mwp.directionToNext

                    updateWayList(mwp.wayNearest, mw)

                    val way = mwp.wayNearest[0]
                    mwp.crosspoint!!.position = way.crosspoint!!.position
                    mwp.node1 = OsmNode(way.node1!!.position)
                    mwp.node2 = OsmNode(way.node2!!.position)
                    mwp.directionDiff = way.directionDiff
                    mwp.radius = way.radius
                }
            }
        }
    }

    override public fun hasMatch(lon: Int, lat: Int): Boolean {
        for (mwp in waypoints) {
            if (mwp.waypoint!!.position.longitude.toIntLongitude() == lon && mwp.waypoint!!.position.latitude.toIntLatitude() == lat &&
                (mwp.radius < this.maxDistance || mwp.crosspoint != null)
            ) {
                return true
            }
        }
        return false
    }

    // check limit of list size (avoid long runs)
    public fun updateWayList(ways: MutableList<MatchedWaypoint>, mw: MatchedWaypoint?) {
        ways.add(mw!!)
        // use only shortest distances by smallest direction difference
        ways.sortWith(comparator!!)
        if (ways.size > MAX_POINTS) ways.removeAt(MAX_POINTS)
    }


    public companion object {
        private const val MAX_POINTS = 5
    }
}
