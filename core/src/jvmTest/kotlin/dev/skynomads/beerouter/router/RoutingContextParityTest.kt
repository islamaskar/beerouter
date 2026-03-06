package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingContextParityTest {
    private val root = File(System.getProperty("user.dir"))
    private val segmentFile = File(
        File(System.getProperty("java.io.tmpdir")), "E5_N45.rd5"
    )

    private fun waypoint(name: String, lat: Double, lon: Double): btools.router.OsmNodeNamed {
        return btools.router.OsmNodeNamed().apply {
            this.name = name
            ilon = lon.toIntLongitude()
            ilat = lat.toIntLatitude()
        }
    }

    @Test
    fun shortestProfileGlobalFlagsMatchBrouter() {
        val profile = File(root, "../misc/profiles2/shortest.brf")

        val brouterContext = btools.router.RoutingContext().apply {
            turnInstructionMode = 2
            localFunction = profile.path
        }
        val engine = btools.router.RoutingEngine(
            null,
            null,
            segmentFile.parentFile,
            listOf(
                waypoint("Reutlingen", 48.4914, 9.2043),
                waypoint("Tübingen", 48.5203263, 9.053596)
            ),
            brouterContext
        )
        engine.doRouting(60_000)

        val beerouterContext = routingContextFromFiles(profile, segmentFile.parentFile).apply {
            global.turnInstructionMode = 2
        }

        assertEquals(brouterContext.carMode, beerouterContext.global.carMode, "carMode")
        assertEquals(brouterContext.bikeMode, beerouterContext.global.bikeMode, "bikeMode")
        assertEquals(brouterContext.footMode, beerouterContext.global.footMode, "footMode")
        assertEquals(
            brouterContext.turnInstructionRoundabouts,
            beerouterContext.global.turnInstructionRoundabouts,
            "turnInstructionRoundabouts"
        )
        assertEquals(
            brouterContext.turnInstructionCatchingRange.toDouble(),
            beerouterContext.global.turnInstructionCatchingRange,
            "turnInstructionCatchingRange"
        )
    }
}
