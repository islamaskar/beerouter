package dev.skynomads.beerouter.codec

import dev.skynomads.beerouter.geo.toBeerouter
import org.maplibre.spatialk.geojson.Position as SpatialKPosition

public fun WaypointMatcher.start(
    start: SpatialKPosition,
    target: SpatialKPosition,
    useAsStartWay: Boolean
): Boolean = start(start.toBeerouter(), target.toBeerouter(), useAsStartWay)

public fun WaypointMatcher.transferNode(position: SpatialKPosition) {
    transferNode(position.toBeerouter())
}

public fun WaypointMatcher.hasMatch(position: SpatialKPosition): Boolean = hasMatch(position.toBeerouter())
