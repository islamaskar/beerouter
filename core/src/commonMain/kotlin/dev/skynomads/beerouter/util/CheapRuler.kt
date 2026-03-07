package dev.skynomads.beerouter.util

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.osm.positionOf
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

public object CheapRuler {
    /**
     * Cheap-Ruler implementation
     * See https://blog.mapbox.com/fast-geodesic-approximations-with-cheap-ruler-106f229ad016
     * for more details.
     *
     * Original code is at https://github.com/mapbox/cheap-ruler under ISC license.
     *
     * This is implemented as a Singleton to have a unique cache for the cosine
     * values across all the code.
     */
    // Conversion constants
    public const val ILATLNG_TO_LATLNG: Double = 1e-6 // From integer to degrees
    public const val KILOMETERS_TO_METERS: Int = 1000

    public val DEG_TO_RAD: Double = kotlin.math.PI / 180.0

    // Scale cache constants
    private const val SCALE_CACHE_LENGTH = 1800
    private const val SCALE_CACHE_INCREMENT = 100000

    // SCALE_CACHE_LENGTH cached values between 0 and COS_CACHE_MAX_DEGREES degrees.
    private val SCALE_CACHE: Array<DoubleArray> = Array(SCALE_CACHE_LENGTH) { i ->
        calcKxKyFromILat(i * SCALE_CACHE_INCREMENT + SCALE_CACHE_INCREMENT / 2)
    }

    private fun calcKxKyFromILat(ilat: Int): DoubleArray {
        val lat = DEG_TO_RAD * (ilat * ILATLNG_TO_LATLNG - 90)
        val cos = cos(lat)
        val cos2 = 2 * cos * cos - 1
        val cos3 = 2 * cos * cos2 - cos
        val cos4 = 2 * cos * cos3 - cos2
        val cos5 = 2 * cos * cos4 - cos3

        // Multipliers for converting integer longitude and latitude into distance
        val kxky = DoubleArray(2)
        kxky[0] =
            (111.41513 * cos - 0.09455 * cos3 + 0.00012 * cos5) * ILATLNG_TO_LATLNG * KILOMETERS_TO_METERS
        kxky[1] =
            (111.13209 - 0.56605 * cos2 + 0.0012 * cos4) * ILATLNG_TO_LATLNG * KILOMETERS_TO_METERS
        return kxky
    }

    /**
     * Calculate the degree->meter scale for given latitude
     *
     * @return [lon->meter,lat->meter]
     */
    public fun getLonLatToMeterScales(ilat: Int): DoubleArray =
        SCALE_CACHE[(ilat / SCALE_CACHE_INCREMENT).coerceIn(0, SCALE_CACHE.lastIndex)]

    /**
     * Compute the distance (in meters) between two Position objects.
     */
    public fun distance(p1: Position, p2: Position): Double =
        distance(p1.longitude, p1.latitude, p2.longitude, p2.latitude)

    /**
     * Compute the distance (in meters) between two points represented by their
     * (integer) latitude and longitude.
     */
    public fun distance(ilon1: Int, ilat1: Int, ilon2: Int, ilat2: Int): Double {
        val kxky = getLonLatToMeterScales((ilat1 + ilat2) shr 1)
        val dlon = (ilon1 - ilon2) * kxky[0]
        val dlat = (ilat1 - ilat2) * kxky[1]
        return sqrt(dlat * dlat + dlon * dlon) // in m
    }

    public fun destination(lon1: Int, lat1: Int, distance: Double, angle: Double): IntArray {
        val lonlat2m = getLonLatToMeterScales(lat1)
        val lon2m = lonlat2m[0]
        val lat2m = lonlat2m[1]
        val adjustedAngle = 90.0 - angle
        val radAngle = adjustedAngle * kotlin.math.PI / 180.0
        val st = sin(radAngle)
        val ct = cos(radAngle)

        val lon2 = (0.5 + lon1 + ct * distance / lon2m).toInt()
        val lat2 = (0.5 + lat1 + st * distance / lat2m).toInt()

        return intArrayOf(lon2, lat2)
    }

    public fun destination(origin: Position, distance: Double, angle: Double): Position =
        destination(
            origin.longitude,
            origin.latitude,
            distance,
            angle
        ).let { (longitude, latitude) -> positionOf(longitude, latitude) }
}
