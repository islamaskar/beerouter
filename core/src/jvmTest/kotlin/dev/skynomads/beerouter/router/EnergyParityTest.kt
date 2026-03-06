package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import kotlinx.coroutines.runBlocking
import org.maplibre.spatialk.geojson.Position
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EnergyParityTest {
    private val root = File(System.getProperty("user.dir"))
    private val segmentFile = File(
        File(System.getProperty("java.io.tmpdir")), "E5_N45.rd5"
    )

    private fun waypoint(name: String, lat: Double, lon: Double): OsmNodeNamed {
        val n = OsmNodeNamed()
        n.name = name
        n.position = Position(lon, lat)
        return n
    }

    private fun routeBrouter(profileName: String, waypoints: List<OsmNodeNamed>): btools.router.OsmTrack {
        val profile = File(root, "../misc/profiles2/$profileName")
        val rc = btools.router.RoutingContext().apply {
            turnInstructionMode = 2
            localFunction = profile.path
        }
        val cr = btools.router.RoutingEngine(
            null,
            null,
            segmentFile.parentFile,
            waypoints.map {
                btools.router.OsmNodeNamed().apply {
                    name = it.name
                    ilon = it.position.longitude.toIntLongitude()
                    ilat = it.position.latitude.toIntLatitude()
                }
            },
            rc
        )
        cr.doRouting(60_000)
        return cr.foundTrack
    }

    private suspend fun routeBeerouter(profileName: String, waypoints: List<OsmNodeNamed>): OsmTrack? {
        val profile = File(root, "../misc/profiles2/$profileName")
        val ctx = routingContextFromFiles(profile, segmentFile.parentFile).apply {
            global.turnInstructionMode = TurnInstructionMode.LOCUS
        }
        return RoutingEngine(ctx).doRouting(waypoints)
    }

    @Test
    fun carLongEnergyParity() = runBlocking {
        val waypoints = listOf(
            waypoint("Stuttgart", 48.7758, 9.1829),
            waypoint("Horb am Neckar", 48.4421, 8.6849)
        )
        val btrack = routeBrouter("car-vario.brf", waypoints)
        val beetrack = routeBeerouter("car-vario.brf", waypoints)
        assertNotNull(beetrack)

        assertEquals(
            btrack.energy,
            beetrack.energy,
            "car-long energy mismatch " +
                    "brouter=${btrack.energy} beerouter=${beetrack.energy} " +
                    "firstNodeDelta=${firstEnergyDeltaNode(btrack, beetrack)} " +
                    "firstLinkdistDelta=${firstLinkdistDeltaNode(btrack, beetrack)} " +
                    "beerouterLastNodeEnergy=${beetrack.nodes.last().energy.toInt()} " +
                    "deltaDetails=${firstEnergyDeltaDetails(btrack, beetrack)}"
        )
    }

    @Test
    fun carKinematicModelParamsParity() {
        val profile = File(root, "../misc/profiles2/car-vario.brf")

        val brouterContext = btools.router.RoutingContext().apply {
            turnInstructionMode = 2
            localFunction = profile.path
        }
        val engine = btools.router.RoutingEngine(
            null,
            null,
            segmentFile.parentFile,
            listOf(
                btools.router.OsmNodeNamed().apply {
                    name = "Stuttgart"
                    ilon = 9.1829.toIntLongitude()
                    ilat = 48.7758.toIntLatitude()
                },
                btools.router.OsmNodeNamed().apply {
                    name = "Horb am Neckar"
                    ilon = 8.6849.toIntLongitude()
                    ilat = 48.4421.toIntLatitude()
                }
            ),
            brouterContext
        )
        engine.doRouting(60_000)

        val beerouterContext = routingContextFromFiles(profile, segmentFile.parentFile).apply {
            global.turnInstructionMode = TurnInstructionMode.LOCUS
        }
        runBlocking {
            RoutingEngine(beerouterContext).doRouting(
                listOf(
                    waypoint("Stuttgart", 48.7758, 9.1829),
                    waypoint("Horb am Neckar", 48.4421, 8.6849)
                )
            )
        }

        val bpm = getDeclaredObject(brouterContext, "pm")
        assertTrue(bpm != null, "brouter pm")
        val km = beerouterContext.pm as KinematicModel

        assertDoubleFieldEquals(bpm, "f_roll", km.fRoll)
        assertDoubleFieldEquals(bpm, "f_air", km.fAir)
        assertDoubleFieldEquals(bpm, "f_recup", km.fRecup)
        assertDoubleFieldEquals(bpm, "p_standby", km.pStandby)
        assertDoubleFieldEquals(bpm, "outside_temp", km.outsideTemp)
        assertDoubleFieldEquals(bpm, "recup_efficiency", km.recupEfficiency)
        assertDoubleFieldEquals(bpm, "totalweight", km.totalweight)
        assertDoubleFieldEquals(bpm, "vmax", km.vmax)
    }

    private fun firstEnergyDeltaNode(btrack: btools.router.OsmTrack, track: OsmTrack): String {
        val max = minOf(btrack.nodes.size, track.nodes.size)
        for (i in 0 until max) {
            val b = btrack.nodes[i].energy.toInt()
            val a = track.nodes[i].energy.toInt()
            if (b != a) {
                return "idx=$i b=$b a=$a"
            }
        }
        return "none"
    }

    private fun firstLinkdistDeltaNode(btrack: btools.router.OsmTrack, track: OsmTrack): String {
        val max = minOf(btrack.nodes.size, track.nodes.size)
        for (i in 0 until max) {
            val bm = btrack.nodes[i].message ?: continue
            val am = track.nodes[i].message ?: continue
            val b = getDeclaredInt(bm, "linkdist")
            val a = am.linkdist
            if (b != a) {
                return "idx=$i b=$b a=$a"
            }
        }
        return "none"
    }

    private fun firstEnergyDeltaDetails(btrack: btools.router.OsmTrack, track: OsmTrack): String {
        val max = minOf(btrack.nodes.size, track.nodes.size)
        for (i in 0 until max) {
            val b = btrack.nodes[i].energy.toInt()
            val a = track.nodes[i].energy.toInt()
            if (b != a) {
                val bm = btrack.nodes[i].message
                val am = track.nodes[i].message
                if (bm == null || am == null) return "idx=$i no-message"
                return "idx=$i " +
                        "b(costfactor=${getDeclaredFloat(bm, "costfactor")}, turn=${getDeclaredFloat(bm, "turnangle")}, " +
                        "prio=${getDeclaredInt(bm, "priorityclassifier")}, vmax=${getDeclaredInt(bm, "vmax")}, " +
                        "vmin=${getDeclaredInt(bm, "vmin")}, vmaxExplicit=${getDeclaredInt(bm, "vmaxExplicit")}, extraTime=${getDeclaredInt(bm, "extraTime")}, " +
                        "ele=${getDeclaredShort(bm, "ele")}, linkdist=${getDeclaredInt(bm, "linkdist")}) " +
                        "a(costfactor=${am.costfactor}, turn=${am.turnangle}, prio=${am.priorityclassifier}, vmax=${am.vmax}, " +
                        "vmin=${am.vmin}, vmaxExplicit=${am.vmaxExplicit}, extraTime=${am.extraTime}, ele=${am.ele}, linkdist=${am.linkdist})"
            }
        }
        return "none"
    }

    private fun getDeclaredInt(target: Any, field: String): Int {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.getInt(target)
    }

    private fun getDeclaredFloat(target: Any, field: String): Float {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.getFloat(target)
    }

    private fun getDeclaredShort(target: Any, field: String): Short {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.getShort(target)
    }

    private fun getDeclaredObject(target: Any, field: String): Any? {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        return f.get(target)
    }

    private fun assertDoubleFieldEquals(target: Any, field: String, actual: Double) {
        val f = target.javaClass.getDeclaredField(field)
        f.isAccessible = true
        val expected = f.getDouble(target)
        assertEquals(expected, actual, 1e-9, field)
    }
}
