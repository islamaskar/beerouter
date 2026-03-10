package dev.skynomads.beerouter.geo

import kotlin.math.roundToInt

private const val COORDINATE_SCALE = 1e-6
private const val LONGITUDE_OFFSET = 180.0
private const val LATITUDE_OFFSET = 90.0
private const val ALTITUDE_SCALE: Double = 4.0
private const val MIN_ENCODED_ALTITUDE: Int = Short.MIN_VALUE.toInt()
private const val MAX_ENCODED_ALTITUDE: Int = Short.MAX_VALUE.toInt()

/**
 * Geographic position stored as E6 integers (~11cm precision).
 * Longitude: 0..360_000_000 (offset by +180°)
 * Latitude:  0..180_000_000 (offset by +90°)
 * Altitude:  quarter meters (−8,192.0m to +8,191.75m)
 */
public data class Position(
    public val longitude: Int,
    public val latitude: Int,
    public val altitude: Short? = null
) {
    /** Unique ID derived from coordinates. Upper 32 bits = longitude, lower 32 bits = latitude. */
    public val id: Long = computeId(longitude, latitude)

    public constructor(longitude: Int, latitude: Int, altitude: Double?) : this(
        longitude = longitude,
        latitude = latitude,
        altitude = altitude.toShortAltitude(),
    )

    public constructor(longitude: Double, latitude: Double, altitude: Double? = null) : this(
        longitude = ((longitude + LONGITUDE_OFFSET) / COORDINATE_SCALE).roundToInt(),
        latitude = ((latitude + LATITUDE_OFFSET) / COORDINATE_SCALE).roundToInt(),
        altitude = altitude.toShortAltitude(),
    )

    public val longitudeDegree: Double
        get() = longitude * COORDINATE_SCALE - LONGITUDE_OFFSET

    public val latitudeDegree: Double
        get() = latitude * COORDINATE_SCALE - LATITUDE_OFFSET

    public val altitudeMeters: Double?
        get() = altitude.toDoubleAltitude()

    public companion object {
        public val ZERO: Position = Position(longitude = 0, latitude = 0)

        public fun computeId(longitude: Int, latitude: Int): Long =
            (longitude.toLong() shl 32) or (latitude.toLong() and 0xFFFFFFFFL)

        public fun from(longitude: Double, latitude: Double, altitude: Double): Position =
            Position(
                longitude = longitude,
                latitude = latitude,
                altitude = altitude,
            )
    }
}

private fun Double?.toShortAltitude(): Short? =
    if (this == null || this.isNaN()) {
        null
    } else {
        (this * ALTITUDE_SCALE)
            .toInt()
            .coerceIn(MIN_ENCODED_ALTITUDE, MAX_ENCODED_ALTITUDE)
            .toShort()
    }

private fun Short?.toDoubleAltitude(): Double? =
    this?.div(ALTITUDE_SCALE)

public fun Long.longitudeFromId(): Int = (this shr 32).toInt()

public fun Long.latitudeFromId(): Int = (this and 0xffffffffL).toInt()

public fun Long.toPosition(): Position = Position(longitudeFromId(), latitudeFromId())
