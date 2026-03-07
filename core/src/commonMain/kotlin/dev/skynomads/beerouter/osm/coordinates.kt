package dev.skynomads.beerouter.osm

import dev.skynomads.beerouter.geo.Position
import kotlin.math.roundToInt

private const val COORDINATE_SCALE = 1e-6
private const val LONGITUDE_OFFSET = 180.0
private const val LATITUDE_OFFSET = 90.0
private const val ELEVATION_SCALE = 4.0
private const val UNSET_ELEVATION: Short = Short.MIN_VALUE
private const val MIN_ENCODED_ELEVATION: Int = Short.MIN_VALUE.toInt() + 1
private const val MAX_ENCODED_ELEVATION: Int = Short.MAX_VALUE.toInt()

public fun Double.toIntLongitude(): Int = ((this + LONGITUDE_OFFSET) / COORDINATE_SCALE).roundToInt()

public fun Double.toIntLatitude(): Int = ((this + LATITUDE_OFFSET) / COORDINATE_SCALE).roundToInt()

public fun Int.toDoubleLongitude(): Double = (this * COORDINATE_SCALE) - LONGITUDE_OFFSET

public fun Int.toDoubleLatitude(): Double = (this * COORDINATE_SCALE) - LATITUDE_OFFSET

public val Position.longitudeE6: Int
    get() = longitude

public val Position.latitudeE6: Int
    get() = latitude

public fun positionOf(longitudeE6: Int, latitudeE6: Int, altitude: Short? = null): Position =
    Position(longitudeE6, latitudeE6, altitude)

public fun positionOf(longitudeE6: Int, latitudeE6: Int, altitude: Double?): Position =
    Position(longitudeE6, latitudeE6, altitude)

public fun osmIdOf(longitudeE6: Int, latitudeE6: Int): Long =
    (longitudeE6.toLong() shl 32) or latitudeE6.toLong()

public fun osmPositionOf(longitudeE6: Int, latitudeE6: Int, sElev: Short): Position =
    positionOf(longitudeE6, latitudeE6, sElev.takeUnless { it == Short.MIN_VALUE })

public fun osmPositionOf(longitudeE6: Int, latitudeE6: Int, sElev: Short?): Position =
    positionOf(longitudeE6, latitudeE6, sElev)

public val Long.longitudeE6: Int
    get() = (this shr 32).toInt()

public val Long.latitudeE6: Int
    get() = (this and 0xffffffffL).toInt()

public fun Long.toPosition(altitude: Short? = null): Position =
    positionOf(longitudeE6, latitudeE6, altitude)

public fun Long.toPosition(altitude: Double?): Position =
    positionOf(longitudeE6, latitudeE6, altitude)

public fun Position.toOsmId(): Long = osmIdOf(longitudeE6, latitudeE6)

public fun toSElev(altitude: Short?): Short = altitude ?: Short.MIN_VALUE

public fun toSElev(altitude: Double?): Short =
    if (altitude == null || altitude.isNaN()) {
        UNSET_ELEVATION
    } else {
        altitude.times(ELEVATION_SCALE)
            .toInt()
            .coerceIn(MIN_ENCODED_ELEVATION, MAX_ENCODED_ELEVATION)
            .toShort()
    }

public fun Short.toAltitude(): Double? =
    if (this == UNSET_ELEVATION) null else this / ELEVATION_SCALE
