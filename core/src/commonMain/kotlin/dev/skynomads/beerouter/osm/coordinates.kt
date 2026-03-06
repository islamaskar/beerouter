package dev.skynomads.beerouter.osm

import org.maplibre.spatialk.geojson.Position
import kotlin.math.roundToInt

public fun Double.toIntLongitude(): Int {
    return ((this + 180.0) / 1e-6).roundToInt()
}

public fun Double.toIntLatitude(): Int {
    return ((this + 90.0) / 1e-6).roundToInt()
}

public fun Int.toDoubleLongitude(): Double {
    return (this * 1e-6) - 180.0
}

public fun Int.toDoubleLatitude(): Double {
    return (this * 1e-6) - 90.0
}

public fun Position.toOsmId(): Long {
    return (longitude.toIntLongitude().toLong()) shl 32 or latitude.toIntLatitude().toLong()
}

public fun toSElev(altitude: Double?): Short =
    altitude?.times(4.0)?.toInt()?.toShort() ?: Short.MIN_VALUE
