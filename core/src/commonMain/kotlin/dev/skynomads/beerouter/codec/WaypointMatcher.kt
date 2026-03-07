package dev.skynomads.beerouter.codec

import dev.skynomads.beerouter.geo.Position

/**
 * a waypoint matcher gets way geometries
 * from the decoder to find the closest
 * matches to the waypoints
 */
public interface WaypointMatcher {
    public fun start(
        start: Position,
        target: Position,
        useAsStartWay: Boolean
    ): Boolean

    public fun transferNode(position: Position)

    public fun end()

    public fun hasMatch(position: Position): Boolean
}
