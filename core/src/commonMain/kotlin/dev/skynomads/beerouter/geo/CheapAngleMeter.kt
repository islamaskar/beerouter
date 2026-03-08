package dev.skynomads.beerouter.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

internal data class AngleMeasurement(
    val angle: Double,
    val cosAngle: Double,
)

internal object CheapAngleMeter {
    fun calcAngle(p0: Position, p1: Position, p2: Position): AngleMeasurement {
        val lonlat2m = CheapRuler.getLonLatToMeterScales(p1.latitude)
        val lon2m = lonlat2m[0]
        val lat2m = lonlat2m[1]
        val dx10 = (p1.longitude - p0.longitude) * lon2m
        val dy10 = (p1.latitude - p0.latitude) * lat2m
        val dx21 = (p2.longitude - p1.longitude) * lon2m
        val dy21 = (p2.latitude - p1.latitude) * lat2m

        val dd = sqrt((dx10 * dx10 + dy10 * dy10) * (dx21 * dx21 + dy21 * dy21))
        if (dd == 0.0) {
            return AngleMeasurement(angle = 0.0, cosAngle = 1.0)
        }
        var sinp = (dy10 * dx21 - dx10 * dy21) / dd
        val cosp = (dy10 * dy21 + dx10 * dx21) / dd

        var offset = 0.0
        var s2 = sinp * sinp
        if (s2 > 0.5) {
            if (sinp > 0.0) {
                offset = 90.0
                sinp = -cosp
            } else {
                offset = -90.0
                sinp = cosp
            }
            s2 = cosp * cosp
        } else if (cosp < 0.0) {
            sinp = -sinp
            offset = if (sinp > 0.0) -180.0 else 180.0
        }
        return AngleMeasurement(
            angle = offset + sinp * (57.4539 + s2 * (9.57565 + s2 * (4.30904 + s2 * 2.56491))),
            cosAngle = cosp,
        )
    }

    fun getAngle(start: Position, end: Position): Double {
        val xdiff = (end.latitude - start.latitude).toDouble()
        val ydiff = (end.longitude - start.longitude).toDouble()
        return atan2(ydiff, xdiff) * (180.0 / PI)
    }

    fun getDirection(p1: Position, p2: Position): Double =
        normalizeAngle(getAngle(p1, p2))

    fun normalizeAngle(angle: Double): Double =
        if (angle >= 360) {
            angle - (360 * (angle / 360).toInt())
        } else {
            if (angle < 0) angle - (360 * ((angle / 360).toInt() - 1)) else angle
        }

    fun getDifferenceFromDirection(b1: Double, b2: Double): Double {
        var r = (b2 - b1) % 360.0
        if (r < -180.0) r += 360.0
        if (r >= 180.0) r -= 360.0
        return abs(r)
    }
}
