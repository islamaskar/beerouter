/**
 * Container for an osm node
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.MatchedWaypoint
import dev.skynomads.beerouter.mapaccess.OsmNode
import dev.skynomads.beerouter.osm.positionOf
import dev.skynomads.beerouter.util.CheapRuler.distance
import dev.skynomads.beerouter.util.CheapRuler.getLonLatToMeterScales
import kotlin.math.sqrt

public open class OsmNodeNamed : OsmNode {
    public var name: String? = null

    public var radius: Double = 0.0 // radius of nogopoint (in meters)
    public var nogoWeight: Double = 0.0 // weight for nogopoint
    public var isNogo: Boolean = false

    public var type: MatchedWaypoint.Type = MatchedWaypoint.Type.SHAPING

    public constructor()

    public constructor(n: OsmNode) : super(n.position)

    override fun toString(): String =
        if (nogoWeight.isNaN()) "${position.longitude},${position.latitude},$name"
        else "${position.longitude},${position.latitude},$name,$nogoWeight"

    public fun distanceWithinRadius(
        lon1: Int,
        lat1: Int,
        lon2: Int,
        lat2: Int,
        totalSegmentLength: Double
    ): Double {
        val centerLon = position.longitude
        val centerLat = position.latitude
        var currentLon1 = lon1
        var currentLat1 = lat1
        var currentLon2 = lon2
        var currentLat2 = lat2
        val lonlat2m = getLonLatToMeterScales((currentLat1 + currentLat2) shr 1)

        var isFirstPointWithinCircle = distance(currentLon1, currentLat1, centerLon, centerLat) < radius
        var isLastPointWithinCircle = distance(currentLon2, currentLat2, centerLon, centerLat) < radius
        // First point is within the circle
        if (isFirstPointWithinCircle) {
            // Last point is within the circle
            if (isLastPointWithinCircle) {
                return totalSegmentLength
            }
            // Last point is not within the circle
            // Just swap points and go on with first first point not within the
            // circle now.
            // Swap coordinates
            val tempLon = currentLon2
            currentLon2 = currentLon1
            currentLon1 = tempLon

            val tempLat = currentLat2
            currentLat2 = currentLat1
            currentLat1 = tempLat

            // Fix boolean values
            isLastPointWithinCircle = isFirstPointWithinCircle
            isFirstPointWithinCircle = false
        }
        // Distance between the initial point and projection of center of
        // the circle on the current segment.
        val initialToProject: Double =
            (((currentLon2 - currentLon1) * (centerLon - currentLon1) * lonlat2m[0] * lonlat2m[0]
                    + (currentLat2 - currentLat1) * (centerLat - currentLat1) * lonlat2m[1] * lonlat2m[1]
                    ) / totalSegmentLength)
        // Distance between the initial point and the center of the circle.
        val initialToCenter = distance(centerLon, centerLat, currentLon1, currentLat1)
        // Half length of the segment within the circle
        val halfDistanceWithin = sqrt(
            radius * radius - (initialToCenter * initialToCenter -
                    initialToProject * initialToProject
                    )
        )
        // Last point is within the circle
        return if (isLastPointWithinCircle) {
            halfDistanceWithin + (totalSegmentLength - initialToProject)
        } else {
            2 * halfDistanceWithin
        }
    }

    public companion object {
        public fun decodeNogo(s: String): OsmNodeNamed {
            val parts = s.split(',', limit = 4)
            require(parts.size >= 3) { "invalid nogo format: $s" }

            val lon = parts[0].toInt()
            val lat = parts[1].toInt()
            val name = parts[2]
            val nogoWeight = parts.getOrNull(3)?.toDouble() ?: Double.NaN

            return OsmNodeNamed().apply {
                this.name = name
                this.nogoWeight = nogoWeight
                isNogo = true
                position = positionOf(lon, lat)
            }
        }
    }
}
