package dev.skynomads.beerouter.util

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import org.maplibre.spatialk.geojson.Position
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

public object CheapRuler {
    /**
     * Cheap-Ruler Java implementation
     * See
     * https://blog.mapbox.com/fast-geodesic-approximations-with-cheap-ruler-106f229ad016
     * for more details.
     *
     *
     * Original code is at https://github.com/mapbox/cheap-ruler under ISC license.
     *
     *
     * This is implemented as a Singleton to have a unique cache for the cosine
     * values across all the code.
     */
    // Conversion constants
    public const val ILATLNG_TO_LATLNG: Double = 1e-6 // From integer to degrees
    public const val KILOMETERS_TO_METERS: Int = 1000

    @JvmField
    public val DEG_TO_RAD: Double = kotlin.math.PI / 180.0

    // Scale cache constants
    private const val SCALE_CACHE_LENGTH = 1800
    private const val SCALE_CACHE_INCREMENT = 100000

    // SCALE_CACHE_LENGTH cached values between 0 and COS_CACHE_MAX_DEGREES degrees.
    private val SCALE_CACHE: Array<DoubleArray?> = Array(SCALE_CACHE_LENGTH) { i ->
        calcKxKyFromILat(i * SCALE_CACHE_INCREMENT + SCALE_CACHE_INCREMENT / 2)
    }

    /**
     * build the cache of cosine values.
     */
    init {
        // Cache is pre-populated in the array initialization above
    }

    private fun calcKxKyFromILat(ilat: Int): DoubleArray {
        val lat = DEG_TO_RAD * (ilat * ILATLNG_TO_LATLNG - 90)
        val cos = cos(lat)
        val cos2 = 2 * cos * cos - 1
        val cos3 = 2 * cos * cos2 - cos
        val cos4 = 2 * cos * cos3 - cos2
        val cos5 = 2 * cos * cos4 - cos3

        // Multipliers for converting integer longitude and latitude into distance
        // (http://1.usa.gov/1Wb1bv7)
        val kxky = DoubleArray(2)
        kxky[0] =
            (111.41513 * cos - 0.09455 * cos3 + 0.00012 * cos5) * ILATLNG_TO_LATLNG * KILOMETERS_TO_METERS
        kxky[1] =
            (111.13209 - 0.56605 * cos2 + 0.0012 * cos4) * ILATLNG_TO_LATLNG * KILOMETERS_TO_METERS
        return kxky
    }

    /**
     * Calculate the degree-&gt;meter scale for given latitude
     *
     * @return [lon-&gt;meter,lat-&gt;meter]
     */
    @JvmStatic
    public fun getLonLatToMeterScales(ilat: Int): DoubleArray? {
        return SCALE_CACHE[ilat / SCALE_CACHE_INCREMENT]
    }

    /**
     * Compute the distance (in meters) between two Position objects.
     *
     * @param p1 First position
     * @param p2 Second position
     * @return The distance between the two points, in meters.
     */
    @JvmStatic
    public fun distance(p1: Position, p2: Position): Double {
        val ilon1 = p1.longitude.toIntLongitude()
        val ilat1 = p1.latitude.toIntLatitude()
        val ilon2 = p2.longitude.toIntLongitude()
        val ilat2 = p2.latitude.toIntLatitude()
        return distance(ilon1, ilat1, ilon2, ilat2)
    }

    /**
     * Compute the distance (in meters) between two points represented by their
     * (integer) latitude and longitude.
     *
     * @param ilon1 Integer longitude for the start point. this is (longitude in degrees + 180) * 1e6.
     * @param ilat1 Integer latitude for the start point, this is (latitude + 90) * 1e6.
     * @param ilon2 Integer longitude for the end point, this is (longitude + 180) * 1e6.
     * @param ilat2 Integer latitude for the end point, this is (latitude + 90) * 1e6.
     * @return The distance between the two points, in meters.
     *
     *
     * Note:
     * Integer longitude is ((longitude in degrees) + 180) * 1e6.
     * Integer latitude is ((latitude in degrees) + 90) * 1e6.
     */
    @JvmStatic
    public fun distance(ilon1: Int, ilat1: Int, ilon2: Int, ilat2: Int): Double {
        val kxky = getLonLatToMeterScales((ilat1 + ilat2) shr 1)!!
        val dlon = (ilon1 - ilon2) * kxky[0]
        val dlat = (ilat1 - ilat2) * kxky[1]
        return sqrt(dlat * dlat + dlon * dlon) // in m
    }

    @JvmStatic
    public fun destination(lon1: Int, lat1: Int, distance: Double, angle: Double): IntArray {
        val lonlat2m = getLonLatToMeterScales(lat1)!!
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
}
