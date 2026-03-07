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

public val Position.longitudeE6: Int
    get() = longitude.toIntLongitude()

public val Position.latitudeE6: Int
    get() = latitude.toIntLatitude()

public fun positionOf(longitudeE6: Int, latitudeE6: Int, altitude: Double? = null): Position =
    Position(longitudeE6.toDoubleLongitude(), latitudeE6.toDoubleLatitude(), altitude)

public fun osmPositionOf(longitudeE6: Int, latitudeE6: Int, sElev: Short): Position =
    positionOf(longitudeE6, latitudeE6, sElev.toAltitude())

public val Long.longitudeE6: Int
    get() = (this shr 32).toInt()

public val Long.latitudeE6: Int
    get() = (this and 0xffffffffL).toInt()

public fun Long.toPosition(altitude: Double? = null): Position =
    positionOf(longitudeE6, latitudeE6, altitude)

public fun Position.toOsmId(): Long {
    return (longitudeE6.toLong()) shl 32 or latitudeE6.toLong()
}

public fun toSElev(altitude: Double?): Short =
    altitude?.times(ELEVATION_SCALE)?.toInt()?.toShort() ?: Short.MIN_VALUE

public fun Short.toAltitude(): Double? =
    if (this == Short.MIN_VALUE) null else this / ELEVATION_SCALE
