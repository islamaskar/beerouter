package app.pedalq.routing

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BikeRouterTest {
    private val profiles = "/Users/islamaskar/pedalcue-phase2a/modules/pedalq-router/android/src/main/assets/brouter"

    @Test
    fun routesLikeBrouterAsItsGeojson() {
        val json = BikeRouter.route("/Users/islamaskar/pedalcue-maps/e2e", "$profiles/trekking.brf", "$profiles/lookups.dat", doubleArrayOf(8.78, 50.01, 8.457, 50.232), 0)
        assertTrue(json.contains("\"track-length\":\"44625\""), json.take(300))
        assertTrue(json.contains("\"filtered ascend\":\"826\""), json.take(300))
        // the stretches add up to the route, with real OSM tags
        val rows = Regex("""\["(-?\d+)","(-?\d+)","(-?\d+)","(\d+)",""").findAll(json).map { it.groupValues[4].toInt() }.toList()
        assertTrue(rows.isNotEmpty())
        assertEquals(44625, rows.sum(), "sum of Distance")
        assertTrue(json.contains("highway="), "WayTags")
        // starts at Offenbach (lon, lat, ele)
        val first = Regex("""coordinates":\[\[([\d.]+),([\d.]+),([\d.]+)]""").find(json)!!.groupValues
        assertEquals(8.78, first[1].toDouble(), 0.01)
        assertEquals(50.01, first[2].toDouble(), 0.01)
        println("PEDALQ geojson ${json.length} chars, ${rows.size} stretches, starts ${first.drop(1)}")
    }
}
