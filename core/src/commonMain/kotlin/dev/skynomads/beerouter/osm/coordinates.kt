package dev.skynomads.beerouter.osm

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
