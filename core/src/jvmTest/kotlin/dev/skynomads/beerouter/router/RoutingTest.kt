package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.SystemFileSystem
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class RoutingTest {
    private data class RouteScenario(
        val name: String,
        val profile: String,
        val waypoints: List<OsmNodeNamed>
    )

    private data class SegmentFixture(
        val name: String,
        val segmentDir: kotlinx.io.files.Path
    )

    fun createWaypoint(name: String, lat: Double, lon: Double): OsmNodeNamed {
        val n = OsmNodeNamed()
        n.name = name
        n.position = Position(lon, lat)
        return n
    }

    @BeforeTest
    fun before() {
        ensureTestSegmentFile()
        ensureGeneratedTestSegmentDir()
    }

    @Test
    fun routingParityMiniTest() {
        val fixtures = listOf(
            SegmentFixture("brouter-rd5", requireNotNull(testSegmentFile.parent)),
            SegmentFixture("beerouter-generated-rd5", generatedTestSegmentDir),
        )
        val scenarios = listOf(
            RouteScenario(
                name = "mini-trekking-tuebingen-rottenburg",
                profile = "trekking.brf",
                waypoints = listOf(
                    createWaypoint("Tübingen", 48.5203263, 9.053596),
                    createWaypoint("Rottenburg", 48.47696, 8.9336788)
                )
            ),
            RouteScenario(
                name = "mini-fastbike-tuebingen-reutlingen",
                profile = "fastbike.brf",
                waypoints = listOf(
                    createWaypoint("Tübingen", 48.5203263, 9.053596),
                    createWaypoint("Reutlingen", 48.4914, 9.2043)
                )
            )
        )

        fixtures.forEach { fixture ->
            assertScenariosEqualBrouter(
                scenarios = scenarios,
                fixture = fixture,
            )
        }
    }

    @Test
    fun routingParityTest() {
        val scenarios = listOf(
            // trekking profile: short / medium / long / via route
            RouteScenario(
                name = "trekking-short-tuebingen-rottenburg",
                profile = "trekking.brf",
                waypoints = listOf(
                    createWaypoint("Tübingen", 48.5203263, 9.053596),
                    createWaypoint("Rottenburg", 48.47696, 8.9336788)
                )
            ),
            RouteScenario(
                name = "trekking-medium-tuebingen-horb",
                profile = "trekking.brf",
                waypoints = listOf(
                    createWaypoint("Tübingen", 48.5203263, 9.053596),
                    createWaypoint("Horb am Neckar", 48.4421, 8.6849)
                )
            ),
            RouteScenario(
                name = "trekking-long-stuttgart-freudenstadt",
                profile = "trekking.brf",
                waypoints = listOf(
                    createWaypoint("Stuttgart", 48.7758, 9.1829),
                    createWaypoint("Freudenstadt", 48.4642, 8.4112)
                )
            ),
            RouteScenario(
                name = "trekking-via-stuttgart-tuebingen-rottenburg",
                profile = "trekking.brf",
                waypoints = listOf(
                    createWaypoint("Stuttgart", 48.7758, 9.1829),
                    createWaypoint("Tübingen", 48.5203263, 9.053596),
                    createWaypoint("Rottenburg", 48.47696, 8.9336788)
                )
            ),
            // fastbike profile: short / medium / via route
            RouteScenario(
                name = "fastbike-short-tuebingen-reutlingen",
                profile = "fastbike.brf",
                waypoints = listOf(
                    createWaypoint("Tübingen", 48.5203263, 9.053596),
                    createWaypoint("Reutlingen", 48.4914, 9.2043)
                )
            ),
            RouteScenario(
                name = "fastbike-medium-tuebingen-boeblingen",
                profile = "fastbike.brf",
                waypoints = listOf(
                    createWaypoint("Tübingen", 48.5203263, 9.053596),
                    createWaypoint("Böblingen", 48.6835, 9.0110)
                )
            ),
            RouteScenario(
                name = "fastbike-via-tuebingen-reutlingen-stuttgart",
                profile = "fastbike.brf",
                waypoints = listOf(
                    createWaypoint("Tübingen", 48.5203263, 9.053596),
                    createWaypoint("Reutlingen", 48.4914, 9.2043),
                    createWaypoint("Stuttgart", 48.7758, 9.1829)
                )
            ),
            // shortest profile: short / long
            RouteScenario(
                name = "shortest-short-reutlingen-tuebingen",
                profile = "shortest.brf",
                waypoints = listOf(
                    createWaypoint("Reutlingen", 48.4914, 9.2043),
                    createWaypoint("Tübingen", 48.5203263, 9.053596)
                )
            ),
            RouteScenario(
                name = "shortest-long-stuttgart-balingen",
                profile = "shortest.brf",
                waypoints = listOf(
                    createWaypoint("Stuttgart", 48.7758, 9.1829),
                    createWaypoint("Balingen", 48.2768, 8.8554)
                )
            ),
            // mtb profile: medium / long
            RouteScenario(
                name = "mtb-medium-tuebingen-balingen",
                profile = "mtb.brf",
                waypoints = listOf(
                    createWaypoint("Tübingen", 48.5203263, 9.053596),
                    createWaypoint("Balingen", 48.2768, 8.8554)
                )
            ),
            RouteScenario(
                name = "mtb-long-stuttgart-freudenstadt",
                profile = "mtb.brf",
                waypoints = listOf(
                    createWaypoint("Stuttgart", 48.7758, 9.1829),
                    createWaypoint("Freudenstadt", 48.4642, 8.4112)
                )
            ),
        )

        assertScenariosEqualBrouter(
            scenarios = scenarios,
            fixture = SegmentFixture("brouter-rd5", requireNotNull(testSegmentFile.parent)),
        )
    }

    private fun assertScenariosEqualBrouter(scenarios: List<RouteScenario>, fixture: SegmentFixture) {
        val failures = mutableListOf<String>()
        scenarios.forEach { scenario ->
            try {
                val btrack = routeBrouter(scenario.waypoints, scenario.profile, fixture.segmentDir)
                val beetrack = runBlocking { routeBeerouter(scenario.waypoints, scenario.profile, fixture.segmentDir) }
                assertTracksEqual("${fixture.name}/${scenario.name}", btrack, beetrack)
            } catch (t: Throwable) {
                failures.add("${fixture.name}/${scenario.name}: ${t.message}")
            }
        }
        assertTrue(
            failures.isEmpty(),
            "Found ${failures.size} brouter parity mismatches:\n${failures.joinToString("\n")}"
        )
    }

    private fun assertTracksEqual(
        scenario: String,
        btrack: btools.router.OsmTrack?,
        beetrack: OsmTrack?
    ) {
        assertNotNull(btrack, "$scenario: brouter returned null track")
        assertNotNull(beetrack, "$scenario: beerouter returned null track")

        assertEquals(btrack.distance, beetrack.distance, "$scenario: distance")
        assertEquals(btrack.ascend, beetrack.ascend, "$scenario: ascend")
        assertEquals(btrack.plainAscend, beetrack.plainAscend, "$scenario: plainAscend")
        assertEquals(btrack.cost, beetrack.cost, "$scenario: cost")
        assertEquals(btrack.energy, beetrack.energy, "$scenario: energy")
        assertEquals(btrack.totalSeconds, beetrack.totalSeconds, "$scenario: totalSeconds")
        assertEquals(btrack.nodes.size, beetrack.nodes.size, "$scenario: node count")

        btrack.nodes.forEachIndexed { idx, node ->
            assertEquals(
                node.iLon,
                beetrack.nodes[idx].position.longitude,
                "$scenario: node[$idx].lon"
            )
            assertEquals(
                node.iLat,
                beetrack.nodes[idx].position.latitude,
                "$scenario: node[$idx].lat"
            )
            assertEquals(
                node.sElev,
                toSElev(beetrack.nodes[idx].position.altitude),
                "$scenario: node[$idx].elev"
            )

            val hint = btrack.getVoiceHint(idx)
            if (hint != null) {
                val actual = beetrack.getVoiceHint(idx)
                assertNotNull(
                    actual,
                    "$scenario: node[$idx] missing voice hint (${
                        describeNearestHints(
                            beetrack,
                            idx
                        )
                    })"
                )
                assertEquals(
                    hint.cruiserCommandString,
                    actual.testCommandString(),
                    "$scenario: node[$idx] voice command"
                )
            }
        }

        assertEquals(List(btrack.nodes.size) { idx ->
            btrack.getVoiceHint(idx)
        }.filterNotNull().size, beetrack.voiceHints.size, "$scenario: voice hint count")
    }

    private fun describeNearestHints(track: OsmTrack, idx: Int): String {
        val nearby = track.voiceHints
            .map { it.indexInTrack to it.testCommandString() }
            .filter { kotlin.math.abs(it.first - idx) <= 3 }
        return if (nearby.isEmpty()) "no nearby hints" else "nearby hints=$nearby"
    }

    fun routeBrouter(
        waypoints: List<OsmNodeNamed>,
        profileName: String,
        segmentDir: kotlinx.io.files.Path,
    ): btools.router.OsmTrack {
        val profile = profilePathForSegments(profileName, segmentDir)
        assertTrue(SystemFileSystem.exists(profile), "profile exists $profile")

        val rc = btools.router.RoutingContext().apply {
            turnInstructionMode = 2
            localFunction = profile.toString()
        }

        val cr = btools.router.RoutingEngine(
            null,
            null,
            segmentDir.toJavaFile(),
            waypoints.map {
                btools.router.OsmNodeNamed().apply {
                    name = it.name
                    ilon = it.position.longitude
                    ilat = it.position.latitude
                }
            },
            rc
        )

        cr.quite = true

        cr.doRouting(60.seconds.inWholeMilliseconds)

        return cr.foundTrack
    }

    suspend fun routeBeerouter(
        waypoints: List<OsmNodeNamed>,
        profileName: String,
        segmentDir: kotlinx.io.files.Path,
    ): OsmTrack? {
        val profile = profilePath(profileName)
        assertTrue(SystemFileSystem.exists(profile), "profile exists $profile")


        val ctx = routingContextFromFiles(profile, segmentDir)

        val engine = RoutingEngine(ctx)

        return engine.doRouting(waypoints)
    }

    private fun toSElev(altitude: Double?): Short =
        altitude?.times(4.0)?.toInt()?.toShort() ?: Short.MIN_VALUE

    private fun toSElev(altitude: Short?): Short = altitude ?: Short.MIN_VALUE
}
