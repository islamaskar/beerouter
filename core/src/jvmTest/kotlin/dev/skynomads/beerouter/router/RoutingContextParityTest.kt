package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.toIntLatitude
import dev.skynomads.beerouter.geo.toIntLongitude
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoutingContextParityTest {
    @BeforeTest
    fun before() {
        ensureTestSegmentFile()
    }

    private fun waypoint(name: String, lat: Double, lon: Double): btools.router.OsmNodeNamed {
        return btools.router.OsmNodeNamed().apply {
            this.name = name
            ilon = lon.toIntLongitude()
            ilat = lat.toIntLatitude()
        }
    }

    @Test
    fun shortestProfileGlobalFlagsMatchBrouter() {
        val profile = profilePath("shortest.brf")

        val brouterContext = btools.router.RoutingContext().apply {
            turnInstructionMode = 2
            localFunction = profile.toString()
        }
        val engine = btools.router.RoutingEngine(
            null,
            null,
            requireNotNull(testSegmentFile.parent).toJavaFile(),
            listOf(
                waypoint("Reutlingen", 48.4914, 9.2043),
                waypoint("Tübingen", 48.5203263, 9.053596)
            ),
            brouterContext
        )
        engine.doRouting(60_000)

        val beerouterContext = routingContextFromFiles(profile, requireNotNull(testSegmentFile.parent))

        assertEquals(brouterContext.carMode, beerouterContext.global.carMode, "carMode")
        assertEquals(brouterContext.bikeMode, beerouterContext.global.bikeMode, "bikeMode")
        assertEquals(brouterContext.footMode, beerouterContext.global.footMode, "footMode")
        assertEquals(
            brouterContext.turnInstructionRoundabouts,
            beerouterContext.global.turnInstructionRoundabouts,
            "turnInstructionRoundabouts"
        )
        assertEquals(
            brouterContext.turnInstructionCatchingRange,
            beerouterContext.global.turnInstructionCatchingRange,
            "turnInstructionCatchingRange"
        )
    }
}
