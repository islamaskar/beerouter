package dev.skynomads.beerouter.geo

import kotlin.math.roundToInt

private const val COORDINATE_SCALE = 1e-6
private const val LONGITUDE_OFFSET = 180.0
private const val LATITUDE_OFFSET = 90.0
private const val ALTITUDE_SCALE: Double = 4.0

/**
 * Geographic position stored as E6 integers (~11cm precision).
 * Longitude: 0..360_000_000 (offset by +180°)
 * Latitude:  0..180_000_000 (offset by +90°)
 * Altitude:  quarter meters (−8,192m to +8,191m)
 */
public data class Position(
    public val longitude: Int,
    public val latitude: Int,
    public val altitude: Short? = null
) {
    /** Unique ID derived from coordinates. Upper 32 bits = longitude, lower 32 bits = latitude. */
    public val id: Long = (longitude.toLong() shl 32) or (latitude.toLong() and 0xFFFFFFFFL)

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

    public fun toSpatialK(): org.maplibre.spatialk.geojson.Position =
        org.maplibre.spatialk.geojson.Position(
            longitude = longitude * COORDINATE_SCALE - LONGITUDE_OFFSET,
            latitude = latitude * COORDINATE_SCALE - LATITUDE_OFFSET,
            altitude = altitudeMeters,
        )

    public val longitudeDegree: Double
        get() = longitude * COORDINATE_SCALE - LONGITUDE_OFFSET

    public val latitudeDegree: Double
        get() = latitude * COORDINATE_SCALE - LATITUDE_OFFSET

    public val altitudeMeters: Double?
        get() = altitude.toDoubleAltitude()

    public fun withAltitude(altitude: Short?): Position = Position(longitude, latitude, altitude)

    public fun withAltitude(altitude: Double?): Position = Position(longitude, latitude, altitude)

    public companion object {
        public fun from(longitude: Double, latitude: Double, altitude: Double): Position =
            Position(
                longitude = longitude,
                latitude = latitude,
                altitude = altitude,
            )

        public fun from(position: org.maplibre.spatialk.geojson.Position): Position =
            Position(position.longitude, position.latitude, position.altitude)
    }
}

private fun Double?.toShortAltitude(): Short? =
    if (this == null || this.isNaN()) null else (this * ALTITUDE_SCALE).toInt().toShort()

private fun Short?.toDoubleAltitude(): Double? =
    this?.div(ALTITUDE_SCALE)
