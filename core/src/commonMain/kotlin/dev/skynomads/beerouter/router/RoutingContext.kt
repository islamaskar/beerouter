/**
 * Container for routig configs
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.expressions.BExpressionContext
import dev.skynomads.beerouter.expressions.BExpressionContextNode
import dev.skynomads.beerouter.expressions.BExpressionContextWay
import dev.skynomads.beerouter.expressions.BExpressionMetaData
import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.geo.coordinateScaleAt
import dev.skynomads.beerouter.mapaccess.GeometryDecoder
import dev.skynomads.beerouter.mapaccess.MapSource
import dev.skynomads.beerouter.mapaccess.MatchedWaypoint
import dev.skynomads.beerouter.mapaccess.OsmLink
import dev.skynomads.beerouter.mapaccess.OsmNode
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

public class RoutingContext(
    private val profileContent: String,
    private val lookupContent: String,
    public val mapSource: MapSource
) {
    public constructor(template: RoutingContext) : this(
        profileContent = template.profileContent,
        lookupContent = template.lookupContent,
        mapSource = template.mapSource
    ) {
        rawTrackSourceFactory = template.rawTrackSourceFactory
        rawAreaSourceFactory = template.rawAreaSourceFactory
        rawAreaSinkFactory = template.rawAreaSinkFactory
    }

    public class Global(ctx: BExpressionContext) {
        public val carMode: Boolean
        public val bikeMode: Boolean
        public val footMode: Boolean
        public val considerTurnRestrictions: Boolean
        public val processUnusedTags: Boolean
        public val forceSecondaryData: Boolean
        public val pass1coefficient: Double
        public val pass2coefficient: Double
        public val elevationpenaltybuffer: Int
        public val elevationmaxbuffer: Int
        public val elevationbufferreduce: Int

        public val cost1speed: Double
        public val additionalcostfactor: Double
        public val changetime: Double
        public val buffertime: Double
        public val waittimeadjustment: Double
        public val inittimeadjustment: Double
        public val starttimeoffset: Double
        public val transitonly: Boolean

        public var waypointCatchingRange: Double
        public var correctMisplacedViaPoints: Boolean
        public val correctMisplacedViaPointsDistance: Double
        public val continueStraight: Boolean
        public var useDynamicDistance: Boolean
        public val buildBeelineOnRange: Boolean

        public var turnInstructionMode: TurnInstructionMode = TurnInstructionMode.NONE
        public val turnInstructionCatchingRange: Double
        public val turnInstructionRoundabouts: Boolean

        // Speed computation model (for bikes)
        public val totalMass: Double
        public val maxSpeed: Double
        public val S_C_x: Double
        public val defaultC_r: Double
        public val bikerPower: Double

        public val showspeed: Boolean
        public val showSpeedProfile: Boolean
        public val inverseRouting: Boolean
        public val showTime: Boolean
        public var hasDirectRouting: Boolean = false

        init {
            carMode = ctx.getVariableValue("validForCars", 0f) != 0f
            bikeMode = ctx.getVariableValue("validForBikes", 0f) != 0f
            footMode = ctx.getVariableValue("validForFoot", 0f) != 0f

            waypointCatchingRange = ctx.getVariableValue("waypointCatchingRange", 250f).toDouble()

            // turn-restrictions not used per default for foot profiles
            considerTurnRestrictions = ctx.getVariableValue(
                "considerTurnRestrictions",
                if (footMode) 0f else 1f
            ) != 0f

            correctMisplacedViaPoints =
                ctx.getVariableValue("correctMisplacedViaPoints", 0f) != 0f
            correctMisplacedViaPointsDistance =
                ctx.getVariableValue("correctMisplacedViaPointsDistance", 400f)
                    .toDouble() // 0 == don't use distance

            continueStraight = ctx.getVariableValue("continueStraight", 0f) != 0f

            // process tags not used in the profile (to have them in the data-tab)
            processUnusedTags = ctx.getVariableValue("processUnusedTags", 0f) != 0f

            forceSecondaryData = ctx.getVariableValue("forceSecondaryData", 0f) != 0f
            pass1coefficient = ctx.getVariableValue("pass1coefficient", 1.5f).toDouble()
            pass2coefficient = ctx.getVariableValue("pass2coefficient", 0f).toDouble()
            elevationpenaltybuffer =
                (ctx.getVariableValue("elevationpenaltybuffer", 5f) * 1000000).toInt()
            elevationmaxbuffer =
                (ctx.getVariableValue("elevationmaxbuffer", 10f) * 1000000).toInt()
            elevationbufferreduce =
                (ctx.getVariableValue("elevationbufferreduce", 0f) * 10000).toInt()

            cost1speed = ctx.getVariableValue("cost1speed", 22f).toDouble()
            additionalcostfactor =
                ctx.getVariableValue("additionalcostfactor", 1.5f).toDouble()
            changetime = ctx.getVariableValue("changetime", 180f).toDouble()
            buffertime = ctx.getVariableValue("buffertime", 120f).toDouble()
            waittimeadjustment =
                ctx.getVariableValue("waittimeadjustment", 0.9f).toDouble()
            inittimeadjustment =
                ctx.getVariableValue("inittimeadjustment", 0.2f).toDouble()
            starttimeoffset = ctx.getVariableValue("starttimeoffset", 0f).toDouble()
            transitonly = ctx.getVariableValue("transitonly", 0f) != 0f

            showspeed = ctx.getVariableValue("showspeed", 0f) != 0f
            showSpeedProfile = ctx.getVariableValue("showSpeedProfile", 0f) != 0f
            inverseRouting = ctx.getVariableValue("inverseRouting", 0f) != 0f
            showTime = ctx.getVariableValue("showtime", 0f) != 0f

            val tiMode = ctx.getVariableValue("turnInstructionMode", 0f).toInt()
            if (tiMode != TurnInstructionMode.AUTO.id) {
                // Preserve externally provided mode for profile auto mode ("1"), as in brouter.
                turnInstructionMode = TurnInstructionMode.fromId(tiMode) ?: TurnInstructionMode.NONE
            }

            turnInstructionCatchingRange =
                ctx.getVariableValue("turnInstructionCatchingRange", 40f).toDouble()
            turnInstructionRoundabouts =
                ctx.getVariableValue("turnInstructionRoundabouts", 1f) != 0f

            // Speed computation model (for bikes)
            // Total mass (biker + bike + luggages or hiker), in kg
            totalMass = ctx.getVariableValue("totalMass", 90f).toDouble()
            // Max speed (before braking), in km/h in profile and m/s in code
            maxSpeed = if (footMode) {
                ctx.getVariableValue("maxSpeed", 6f) / 3.6
            } else {
                ctx.getVariableValue("maxSpeed", 45f) / 3.6
            }
            // Equivalent surface for wind, S * C_x, F = -1/2 * S * C_x * v^2 = - S_C_x * v^2
            S_C_x = ctx.getVariableValue("S_C_x", 0.5f * 0.45f).toDouble()
            // Default resistance of the road, F = - m * g * C_r (for good quality road)
            defaultC_r = ctx.getVariableValue("C_r", 0.01f).toDouble()
            // Constant power of the biker (in W)
            bikerPower = ctx.getVariableValue("bikerPower", 100f).toDouble()

            useDynamicDistance = ctx.getVariableValue("use_dynamic_range", 1f) == 1f
            buildBeelineOnRange = ctx.getVariableValue("add_beeline", 0f) == 1f

            val test = ctx.getVariableValue("check_start_way", 1f) == 1f
            if (!test) ctx.freeNoWays()
        }
    }

    public var alternativeIdx: Int = 0

    public var profileTimestamp: Long = 0

    public var keyValues: MutableMap<String, String> = mutableMapOf()

    public var rawTrackSourceFactory: (() -> Source)? = null
    public var rawAreaSourceFactory: (() -> Source)? = null
    public var rawAreaSinkFactory: (() -> Sink)? = null

    public val global: Global
    public val way: BExpressionContextWay
    public val node: BExpressionContextNode

    public var geometryDecoder: GeometryDecoder = GeometryDecoder()

    public var memoryclass: Int = 64

    public var ai: AreaInfo? = null
    public lateinit var pm: OsmPathModel

    init {
        val meta = BExpressionMetaData()

        node = BExpressionContextNode(0, meta)
        way = BExpressionContextWay(memoryclass * 512, meta)
        node.setForeignContext(way)

        meta.readMetaData(lookupContent)

        way.parseProfile(profileContent, "global", keyValues)
        node.parseProfile(profileContent, "global", keyValues)

        setModel(way._modelClass)
        global = Global(way)

        if (global.processUnusedTags) {
            way.setAllTagsUsed()
        }
    }

    public fun freeNoWays() {
        way.freeNoWays()
    }

    public var poipoints: MutableList<OsmNodeNamed> = mutableListOf()
    public var nogopoints: MutableList<OsmNodeNamed> = mutableListOf()

    private var nogopoints_all: MutableList<OsmNodeNamed> =
        mutableListOf() // full list not filtered for wayoints-in-nogos
    private var keepnogopoints: MutableList<OsmNodeNamed> = mutableListOf()
    private var pendingEndpoint: OsmNodeNamed? = null

    public var startDirection: Int? = null
    public var startDirectionValid: Boolean = false
    public var forceUseStartDirection: Boolean = false
    public var roundTripDistance: Int? = null
    public var roundTripDirectionAdd: Int? = null
    public var roundTripPoints: Int? = null
    public var allowSamewayback: Boolean = false

    public var nogoCost: Double = 0.0
    public var isEndpoint: Boolean = false

    public var shortestmatch: Boolean = false
    public var wayfraction: Double = 0.0
    public var shortestPosition: Position? = null

    public var inverseDirection: Boolean = false

    public var exportWaypoints: Boolean = false

    public var firstPrePath: OsmPrePath? = null

    /**
     * restore the full nogolist previously saved by cleanNogoList
     */
    public fun restoreNogoList() {
        nogopoints = nogopoints_all
    }

    /**
     * clean the nogolist (previoulsy saved by saveFullNogolist())
     * by removing nogos with waypoints within
     *
     * @return true if all wayoints are all in the same (full-weigth) nogo area (triggering bee-line-mode)
     */
    public fun cleanNogoList(waypoints: MutableList<OsmNode>) {
        nogopoints_all = nogopoints
        val nogos = mutableListOf<OsmNodeNamed>()
        for (nogo in nogopoints) {
            var goodGuy = true
            for (wp in waypoints) {
                if (wp.calcDistance(nogo) < nogo.radius.toInt()
                    && (nogo !is OsmNogoPolygon || (if (nogo.isClosed)
                        nogo.isWithin(
                            wp.position.longitude.toLong(),
                            wp.position.latitude.toLong()
                        )
                    else
                        nogo.isOnPolyline(
                            wp.position.longitude.toLong(),
                            wp.position.latitude.toLong()
                        )))
                ) {
                    goodGuy = false
                }
            }
            if (goodGuy) nogos.add(nogo)
        }
        nogopoints = nogos
    }

    public fun checkMatchedWaypointAgainstNogos(matchedWaypoints: MutableList<MatchedWaypoint>) {
        if (nogopoints.isEmpty()) return
        val theSize = matchedWaypoints.size
        if (theSize < 2) return
        var removed = 0
        val newMatchedWaypoints = mutableListOf<MatchedWaypoint>()
        var prevMwp: MatchedWaypoint? = null
        var prevMwpIsInside = false
        for (i in 0..<theSize) {
            val mwp = matchedWaypoints[i]
            var isInsideNogo = false
            val wp = requireNotNull(mwp.crosspoint)
            for (nogo in nogopoints) {
                if (nogo.nogoWeight.isNaN()
                    && wp.calcDistance(nogo) < nogo.radius && (nogo !is OsmNogoPolygon || (if (nogo.isClosed)
                        nogo.isWithin(
                            wp.position.longitude.toLong(),
                            wp.position.latitude.toLong()
                        )
                    else
                        nogo.isOnPolyline(
                            wp.position.longitude.toLong(),
                            wp.position.latitude.toLong()
                        )))
                ) {
                    isInsideNogo = true
                    break
                }
            }
            if (isInsideNogo) {
                var useAnyway = false
                if (prevMwp == null) useAnyway = true
                else if (mwp.type == MatchedWaypoint.Type.DIRECT) useAnyway = true
                else if (prevMwp.type == MatchedWaypoint.Type.DIRECT) useAnyway = true
                else if (prevMwpIsInside) useAnyway = true
                else require(i != theSize - 1) { "last wpt in restricted area " }
                if (useAnyway) {
                    prevMwpIsInside = true
                    newMatchedWaypoints.add(mwp)
                } else {
                    removed++
                    prevMwpIsInside = false
                }
            } else {
                prevMwpIsInside = false
                newMatchedWaypoints.add(mwp)
            }
            prevMwp = mwp
        }
        require(newMatchedWaypoints.size >= 2) { "a wpt in restricted area " }
        if (removed > 0) {
            matchedWaypoints.clear()
            matchedWaypoints.addAll(newMatchedWaypoints)
        }
    }

    public fun allInOneNogo(waypoints: MutableList<OsmNode>): Boolean {
        if (nogopoints.isEmpty()) return false
        var allInTotal = false
        for (nogo in nogopoints) {
            var allIn = nogo.nogoWeight.isNaN()
            for (wp in waypoints) {
                val dist = wp.calcDistance(nogo)
                if (dist < nogo.radius
                    && (nogo !is OsmNogoPolygon || (if (nogo.isClosed)
                        nogo.isWithin(
                            wp.position.longitude.toLong(),
                            wp.position.latitude.toLong()
                        )
                    else
                        nogo.isOnPolyline(
                            wp.position.longitude.toLong(),
                            wp.position.latitude.toLong()
                        )))
                ) {
                    continue
                }
                allIn = false
            }
            allInTotal = allInTotal or allIn
        }
        return allInTotal
    }

    public val nogoChecksums: LongArray
        get() {
            val cs = LongArray(3)
            for (nogo in nogopoints) {
                cs[0] += nogo.position.longitude
                cs[1] += nogo.position.latitude
                // 10 is an arbitrary constant to get sub-integer precision in the checksum
                cs[2] += (nogo.radius * 10.0).toLong()
            }
            return cs
        }

    public fun setWaypoint(wp: OsmNodeNamed?, endpoint: Boolean) {
        setWaypoint(wp, null, endpoint)
    }

    public fun setWaypoint(wp: OsmNodeNamed?, pendingEndpoint: OsmNodeNamed?, endpoint: Boolean) {
        val waypoint = requireNotNull(wp)
        keepnogopoints = nogopoints
        nogopoints = mutableListOf(waypoint)
        if (keepnogopoints.isNotEmpty()) nogopoints.addAll(keepnogopoints)
        isEndpoint = endpoint
        this.pendingEndpoint = pendingEndpoint
    }

    private fun setModel(className: String?) {
        val model = if (className == null) {
            StdModel()
        } else {
            when (normalizeModelName(className)) {
                "StdModel" -> StdModel()
                "KinematicModel" -> KinematicModel()
                else -> throw RuntimeException("Cannot create path-model: unsupported model '$className'")
            }
        }
        model.init(way, node, keyValues)
        pm = model
    }

    private fun normalizeModelName(className: String): String {
        val effectiveClassName = when {
            className.startsWith("btools.router.") ->
                className.replace("btools.router.", "dev.skynomads.beerouter.router.")

            else -> className
        }
        return effectiveClassName.substringAfterLast('.')
    }

    public fun checkPendingEndpoint(): Boolean {
        val pendingEndpoint = pendingEndpoint
        if (pendingEndpoint != null) {
            isEndpoint = true
            nogopoints[0] = pendingEndpoint
            this.pendingEndpoint = null
            return true
        }
        return false
    }

    public fun unsetWaypoint() {
        nogopoints = keepnogopoints
        pendingEndpoint = null
        isEndpoint = false
    }

    public fun calcDistance(start: Position, end: Position): Int {
        var lon1 = start.longitude
        var lat1 = start.latitude
        var lon2 = end.longitude
        var lat2 = end.latitude
        val scale = coordinateScaleAt((start.latitude + end.latitude) shr 1)
        val dlon2m = scale.longitudeToMeters
        val dlat2m = scale.latitudeToMeters
        var dx = (lon2 - lon1) * dlon2m
        var dy = (lat2 - lat1) * dlat2m
        var d = sqrt(dy * dy + dx * dx)

        shortestmatch = false
        shortestPosition = null

        if (nogopoints.isNotEmpty() && d > 0.0) {
            for (nogo in nogopoints) {
                val nogoPosition = nogo.position
                val x1: Double = (lon1 - nogoPosition.longitude) * dlon2m
                val y1 = (lat1 - nogoPosition.latitude) * dlat2m
                val x2: Double = (lon2 - nogoPosition.longitude) * dlon2m
                val y2 = (lat2 - nogoPosition.latitude) * dlat2m
                val r12 = x1 * x1 + y1 * y1
                val r22 = x2 * x2 + y2 * y2
                var radius = abs(if (r12 < r22) y1 * dx - x1 * dy else y2 * dx - x2 * dy) / d

                if (radius < nogo.radius) { // 20m
                    var s1 = x1 * dx + y1 * dy
                    var s2 = x2 * dx + y2 * dy


                    if (s1 < 0.0) {
                        s1 = -s1
                        s2 = -s2
                    }
                    if (s2 > 0.0) {
                        radius = sqrt(if (s1 < s2) r12 else r22)
                        if (radius > nogo.radius) continue
                    }
                    if (nogo.isNogo) {
                        if (nogo !is OsmNogoPolygon) {  // nogo is a circle
                            nogoCost = if (nogo.nogoWeight.isNaN()) {
                                // default nogo behaviour (ignore completely)
                                -1.0
                            } else {
                                // nogo weight, compute distance within the circle
                                nogo.distanceWithinRadius(
                                    Position(lon1, lat1),
                                    Position(lon2, lat2),
                                    d
                                ) * nogo.nogoWeight
                            }
                        } else if (nogo.intersects(Position(lon1, lat1), Position(lon2, lat2))) {
                            // nogo is a polyline/polygon, we have to check there is indeed
                            // an intersection in this case (radius check is not enough).
                            if (nogo.nogoWeight.isNaN()) {
                                // default nogo behaviour (ignore completely)
                                nogoCost = -1.0
                            } else {
                                nogoCost = if (nogo.isClosed) {
                                    // compute distance within the polygon
                                    nogo.distanceWithinPolygon(
                                        Position(lon1, lat1),
                                        Position(lon2, lat2)
                                    ) * nogo.nogoWeight
                                } else {
                                    // for a polyline, just add a constant penalty
                                    nogo.nogoWeight
                                }
                            }
                        }
                    } else {
                        shortestmatch = true
                        nogo.radius = radius // shortest distance to way
                        // calculate remaining distance
                        if (s2 < 0.0) {
                            wayfraction = -s2 / (d * d)
                            val xm = x2 - wayfraction * dx
                            val ym = y2 - wayfraction * dy
                            shortestPosition = Position(
                                (xm / dlon2m + nogoPosition.longitude).toInt(),
                                (ym / dlat2m + nogoPosition.latitude).toInt()
                            )
                        } else if (s1 > s2) {
                            wayfraction = 0.0
                            shortestPosition = Position(lon2, lat2)
                        } else {
                            wayfraction = 1.0
                            shortestPosition = Position(lon1, lat1)
                        }

                        // here it gets nasty: there can be nogo-points in the list
                        // *after* the shortest distance point. In case of a shortest-match
                        // we use the reduced way segment for nogo-matching, in order not
                        // to cut our escape-way if we placed a nogo just in front of where we are
                        if (isEndpoint) {
                            wayfraction = 1.0 - wayfraction
                            lon2 = requireNotNull(shortestPosition).longitude
                            lat2 = requireNotNull(shortestPosition).latitude
                        } else {
                            nogoCost = 0.0
                            lon1 = requireNotNull(shortestPosition).longitude
                            lat1 = requireNotNull(shortestPosition).latitude
                        }
                        dx = (lon2 - lon1) * dlon2m
                        dy = (lat2 - lat1) * dlat2m
                        d = sqrt(dy * dy + dx * dx)
                    }
                }
            }
        }
        return max(1.0, d.roundToInt().toDouble()).toInt()
    }

    public fun createPrePath(origin: OsmPath, link: OsmLink): OsmPrePath? {
        val p = pm.createPrePath()
        p?.init(origin, link, this)
        return p
    }

    public fun createPath(link: OsmLink): OsmPath {
        val p = pm.createPath()
        p.init(link)
        return p
    }

    public fun createPath(
        origin: OsmPath,
        link: OsmLink,
        refTrack: OsmTrack?,
        detailMode: Boolean
    ): OsmPath {
        val p = pm.createPath()
        p.init(origin, link, refTrack, detailMode, this)
        return p
    }

    public companion object {
        public fun prepareNogoPoints(nogos: MutableList<OsmNodeNamed>) {
            for (nogo in nogos) {
                if (nogo is OsmNogoPolygon) {
                    continue
                }
                var s = requireNotNull(nogo.name)
                val idx = s.indexOf(' ')
                if (idx > 0) s = s.take(idx)
                var ir = 20 // default radius
                if (s.length > 4) {
                    try {
                        ir = s.substring(4).toInt()
                    } catch (e: Exception) { /* ignore */
                    }
                }
                // Radius of the nogo point in meters
                nogo.radius = ir.toDouble()
            }
        }
    }
}
