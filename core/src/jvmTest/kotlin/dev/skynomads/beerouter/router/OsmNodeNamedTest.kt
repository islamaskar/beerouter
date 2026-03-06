package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import dev.skynomads.beerouter.util.CheapRuler.destination
import dev.skynomads.beerouter.util.CheapRuler.distance
import org.maplibre.spatialk.geojson.Position
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OsmNodeNamedTest {
    @Test
    fun testDistanceWithinRadius() {
        var lat1 = 0
        var lat2 = 0
        var lon1 = 0
        var lon2 = 0
        // Circle definition
        val node = OsmNodeNamed()
        // Center
        node.position = Position(2.334243, 48.824017)
        // Radius
        node.radius = 30.0

        // Check distance within radius is correctly computed if the segment passes through the center
        lon1 = 2.332559.toIntLongitude()
        lat1 = 48.823822.toIntLatitude()
        // Segment ends
        lon2 = 2.335018.toIntLongitude()
        lat2 = 48.824105.toIntLatitude()
        var totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        assertEquals(
            2 * node.radius,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.01 * (2 * node.radius),
            "Works for segment aligned with the nogo center"
        )

        // Check distance within radius is correctly computed for a given circle
        node.position = Position(2.33438, 48.824275)
        assertEquals(
            27.5,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 27.5,
            "Works for a segment with no particular properties"
        )

        // Check distance within radius is the same if we reverse start and end point
        assertEquals(
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            node.distanceWithinRadius(lon2, lat2, lon1, lat1, totalSegmentLength),
            0.01,
            "Works if we switch firs and last point"
        )

        // Check distance within radius is correctly computed if a point is inside the circle
        lon2 = 2.334495.toIntLongitude()
        lat2 = 48.824045.toIntLatitude()
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        assertEquals(
            17.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 17,
            "Works if last point is within the circle"
        )

        lon1 = 2.334495.toIntLongitude()
        lat1 = 48.824045.toIntLatitude()
        lon2 = 2.335018.toIntLongitude()
        lat2 = 48.824105.toIntLatitude()
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        assertEquals(
            9.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 9,
            "Works if first point is within the circle"
        )

        lon1 = 2.33427.toIntLongitude()
        lat1 = 48.82402.toIntLatitude()
        lon2 = 2.334587.toIntLongitude()
        lat2 = 48.824061.toIntLatitude()
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        assertEquals(
            25.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 25,
            "Works if both points are within the circle"
        )

        // Check distance within radius is correctly computed if both points are on
        // the same side of the center.
        // Note: the only such case possible is with one point outside and one
        // point within the circle, as we expect the segment to have a non-empty
        // intersection with the circle.
        lon1 = 2.332559.toIntLongitude()
        lat1 = 48.823822.toIntLatitude()
        lon2 = 2.33431.toIntLongitude()
        lat2 = 48.824027.toIntLatitude()
        totalSegmentLength = distance(lon1, lat1, lon2, lat2)
        assertEquals(
            5.0,
            node.distanceWithinRadius(lon1, lat1, lon2, lat2, totalSegmentLength),
            0.1 * 5,
            "Works if both points are on the same side of the circle center"
        )
    }

    @Test
    fun testDestination() {
        // Segment ends
        var lon1: Int
        var lat1: Int
        var lon2: Int
        var lat2: Int
        // Circle definition
        val node = OsmNodeNamed()
        // Center
        node.position = Position(0.0, 0.0)
        val startDist = 1000.0

        var i = 0
        while (i <= 360) {
            val nodeLon = node.position.longitude.toIntLongitude()
            val nodeLat = node.position.latitude.toIntLatitude()
            val pos = destination(nodeLon, nodeLat, startDist, i.toDouble())
            val dist = distance(nodeLon, nodeLat, pos[0], pos[1])
            assertTrue(
                dist - 1 < startDist && dist + 1 > startDist,
                "pos " + pos[0] + " " + pos[1] + " distance (" + dist + ") should be around (" + startDist + ")"
            )
            i += 45
        }
    }
}
