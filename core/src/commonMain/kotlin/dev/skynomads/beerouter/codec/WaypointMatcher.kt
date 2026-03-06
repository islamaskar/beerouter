package dev.skynomads.beerouter.codec

/**
 * a waypoint matcher gets way geometries
 * from the decoder to find the closest
 * matches to the waypoints
 */
public interface WaypointMatcher {
    public fun start(
        ilonStart: Int,
        ilatStart: Int,
        ilonTarget: Int,
        ilatTarget: Int,
        useAsStartWay: Boolean
    ): Boolean

    public fun transferNode(ilon: Int, ilat: Int)

    public fun end()

    public fun hasMatch(lon: Int, lat: Int): Boolean
}
