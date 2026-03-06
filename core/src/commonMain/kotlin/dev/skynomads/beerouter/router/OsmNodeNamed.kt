/**
 * Container for an osm node
 *
 * @author ab
 */
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.mapaccess.MatchedWaypoint
import dev.skynomads.beerouter.mapaccess.OsmNode
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import dev.skynomads.beerouter.osm.toDoubleLatitude
import dev.skynomads.beerouter.osm.toDoubleLongitude
import dev.skynomads.beerouter.util.CheapRuler.distance
import dev.skynomads.beerouter.util.CheapRuler.getLonLatToMeterScales
import org.maplibre.spatialk.geojson.Position
import kotlin.math.sqrt

public open class OsmNodeNamed : OsmNode {
    public var name: String? = null

    public var radius: Double = 0.0 // radius of nogopoint (in meters)
    public var nogoWeight: Double = 0.0 // weight for nogopoint
    public var isNogo: Boolean = false

    public var type: MatchedWaypoint.Type = MatchedWaypoint.Type.SHAPING

    public constructor()

    public constructor(n: OsmNode) : super(n.position.longitude.toIntLongitude(), n.position.latitude.toIntLatitude())

    override fun toString(): String {
        return if (nogoWeight.isNaN()) "${position.longitude.toIntLongitude()},${position.latitude.toIntLatitude()},$name"
        else "${position.longitude.toIntLongitude()},${position.latitude.toIntLatitude()},$name,$nogoWeight"
    }

    public fun distanceWithinRadius(
        lon1: Int,
        lat1: Int,
        lon2: Int,
        lat2: Int,
        totalSegmentLength: Double
    ): Double {
        var currentLon1 = lon1
        var currentLat1 = lat1
        var currentLon2 = lon2
        var currentLat2 = lat2
        val lonlat2m = getLonLatToMeterScales((currentLat1 + currentLat2) shr 1) ?: return 0.0

        var isFirstPointWithinCircle = distance(currentLon1, currentLat1, position.longitude.toIntLongitude(), position.latitude.toIntLatitude()) < radius
        var isLastPointWithinCircle = distance(currentLon2, currentLat2, position.longitude.toIntLongitude(), position.latitude.toIntLatitude()) < radius
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
            (((currentLon2 - currentLon1) * (position.longitude.toIntLongitude() - currentLon1) * lonlat2m[0] * lonlat2m[0]
                    + (currentLat2 - currentLat1) * (position.latitude.toIntLatitude() - currentLat1) * lonlat2m[1] * lonlat2m[1]
                    ) / totalSegmentLength)
        // Distance between the initial point and the center of the circle.
        val initialToCenter = distance(position.longitude.toIntLongitude(), position.latitude.toIntLatitude(), currentLon1, currentLat1)
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
            val n = OsmNodeNamed()
            val idx1 = s.indexOf(',')
            val lon = s.take(idx1).toInt()
            val idx2 = s.indexOf(',', idx1 + 1)
            val lat = s.substring(idx1 + 1, idx2).toInt()
            val idx3 = s.indexOf(',', idx2 + 1)
            if (idx3 == -1) {
                n.name = s.substring(idx2 + 1)
                n.nogoWeight = Double.NaN
            } else {
                n.name = s.substring(idx2 + 1, idx3)
                n.nogoWeight = s.substring(idx3 + 1).toDouble()
            }
            n.isNogo = true
            n.position = Position(
                lon.toDoubleLongitude(),
                lat.toDoubleLatitude(),
                0.0
            )
            return n
        }
    }
}
