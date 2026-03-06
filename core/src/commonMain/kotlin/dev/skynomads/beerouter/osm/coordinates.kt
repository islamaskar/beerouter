package dev.skynomads.beerouter.osm

import org.maplibre.spatialk.geojson.Position
import kotlin.math.roundToInt

private const val COORDINATE_SCALE = 1e-6
private const val LONGITUDE_OFFSET = 180.0
private const val LATITUDE_OFFSET = 90.0
private const val ELEVATION_SCALE = 4.0

public fun Double.toIntLongitude(): Int = ((this + LONGITUDE_OFFSET) / COORDINATE_SCALE).roundToInt()

public fun Double.toIntLatitude(): Int = ((this + LATITUDE_OFFSET) / COORDINATE_SCALE).roundToInt()

public fun Int.toDoubleLongitude(): Double = (this * COORDINATE_SCALE) - LONGITUDE_OFFSET

public fun Int.toDoubleLatitude(): Double = (this * COORDINATE_SCALE) - LATITUDE_OFFSET

public fun Position.toOsmId(): Long {
    return (longitude.toIntLongitude().toLong()) shl 32 or latitude.toIntLatitude().toLong()
}

public fun toSElev(altitude: Double?): Short =
    altitude?.times(ELEVATION_SCALE)?.toInt()?.toShort() ?: Short.MIN_VALUE
