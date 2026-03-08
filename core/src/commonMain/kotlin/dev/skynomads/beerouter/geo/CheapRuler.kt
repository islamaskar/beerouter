package dev.skynomads.beerouter.geo

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object CheapRuler {
    private const val ILATLNG_TO_LATLNG: Double = 1e-6
    private const val KILOMETERS_TO_METERS: Int = 1000
    private const val SCALE_CACHE_LENGTH = 1800
    private const val SCALE_CACHE_INCREMENT = 100000

    const val DEG_TO_RAD: Double = kotlin.math.PI / 180.0

    private val scaleCache: Array<DoubleArray> = Array(SCALE_CACHE_LENGTH) { i ->
        calcKxKyFromILat(i * SCALE_CACHE_INCREMENT + SCALE_CACHE_INCREMENT / 2)
    }

    private fun calcKxKyFromILat(ilat: Int): DoubleArray {
        val lat = DEG_TO_RAD * (ilat * ILATLNG_TO_LATLNG - 90)
        val cos = cos(lat)
        val cos2 = 2 * cos * cos - 1
        val cos3 = 2 * cos * cos2 - cos
        val cos4 = 2 * cos * cos3 - cos2
        val cos5 = 2 * cos * cos4 - cos3

        return doubleArrayOf(
            (111.41513 * cos - 0.09455 * cos3 + 0.00012 * cos5) * ILATLNG_TO_LATLNG * KILOMETERS_TO_METERS,
            (111.13209 - 0.56605 * cos2 + 0.0012 * cos4) * ILATLNG_TO_LATLNG * KILOMETERS_TO_METERS,
        )
    }
    fun getLonLatToMeterScales(ilat: Int): DoubleArray =
        scaleCache[(ilat / SCALE_CACHE_INCREMENT).coerceIn(0, scaleCache.lastIndex)]

    fun distance(p1: Position, p2: Position): Double =
        run {
            val kxky = getLonLatToMeterScales((p1.latitude + p2.latitude) shr 1)
            val dlon = (p1.longitude - p2.longitude) * kxky[0]
            val dlat = (p1.latitude - p2.latitude) * kxky[1]
            sqrt(dlat * dlat + dlon * dlon)
        }

    fun destination(origin: Position, distance: Double, angle: Double): Position {
        val lonlat2m = getLonLatToMeterScales(origin.latitude)
        val lon2m = lonlat2m[0]
        val lat2m = lonlat2m[1]
        val adjustedAngle = 90.0 - angle
        val radAngle = adjustedAngle * kotlin.math.PI / 180.0
        val st = sin(radAngle)
        val ct = cos(radAngle)

        val longitude = (0.5 + origin.longitude + ct * distance / lon2m).toInt()
        val latitude = (0.5 + origin.latitude + st * distance / lat2m).toInt()

        return Position(longitude, latitude)
    }
}
