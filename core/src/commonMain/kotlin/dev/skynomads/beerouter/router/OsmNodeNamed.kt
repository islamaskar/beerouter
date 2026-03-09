/**
 * Container for an osm node
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.map.MatchedWaypoint
import dev.skynomads.beerouter.map.OsmNode
import dev.skynomads.beerouter.geo.CheapRuler.distance
import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.geo.coordinateScaleAt
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
        start: Position,
        end: Position,
        totalSegmentLength: Double
    ): Double {
        val center = position
        var currentLon1 = start.longitude
        var currentLat1 = start.latitude
        var currentLon2 = end.longitude
        var currentLat2 = end.latitude
        val scale = coordinateScaleAt((currentLat1 + currentLat2) shr 1)

        var isFirstPointWithinCircle = distance(Position(currentLon1, currentLat1), center) < radius
        var isLastPointWithinCircle = distance(Position(currentLon2, currentLat2), center) < radius
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
            (((currentLon2 - currentLon1) * (center.longitude - currentLon1) * scale.longitudeToMeters * scale.longitudeToMeters
                    + (currentLat2 - currentLat1) * (center.latitude - currentLat1) * scale.latitudeToMeters * scale.latitudeToMeters
                    ) / totalSegmentLength)
        // Distance between the initial point and the center of the circle.
        val initialToCenter = distance(center, Position(currentLon1, currentLat1))
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
                position = Position(lon, lat)
            }
        }
    }
}
