package dev.skynomads.beerouter.benchmark

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.DefaultMapSource
import dev.skynomads.beerouter.router.OsmNodeNamed
import dev.skynomads.beerouter.router.RoutingContext
import dev.skynomads.beerouter.router.RoutingEngine
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.Path
import java.io.File
import kotlin.system.measureTimeMillis

private const val ROUTE_9_FROM_LON = 10.733333
private const val ROUTE_9_FROM_LAT = 51.000000
private const val ROUTE_9_TO_LON = 13.426948
private const val ROUTE_9_TO_LAT = 49.553392

public object SingleRoutePerformance {
    @JvmStatic
    public fun main(args: Array<String>) {
        require(args.size == 3) {
            "usage: SingleRoutePerformance <segmentdir> <profiledir> <profile>"
        }

        val profileDir = File(args[1])
        val profileName = args[2].removeSuffix(".brf") + ".brf"
        val profile = forcePass1Coefficient(File(profileDir, profileName).readText(), "1.5")
        val lookup = File(profileDir, "lookups.dat").readText()
        val ctx = RoutingContext(
            profileContent = profile,
            lookupContent = lookup,
            mapSource = DefaultMapSource(Path(args[0])),
        )
        val engine = RoutingEngine(ctx)
        val waypoints = listOf(
            OsmNodeNamed(Position(ROUTE_9_FROM_LON, ROUTE_9_FROM_LAT)).apply { name = "from9" },
            OsmNodeNamed(Position(ROUTE_9_TO_LON, ROUTE_9_TO_LAT)).apply { name = "to9" },
        )

        val elapsed = measureTimeMillis {
            val track = runBlocking { engine.doRouting(waypoints) }
            requireNotNull(track) { "route 9 returned no track" }
            println("distance_m=${track.distance}")
        }
        println("elapsed_ms=$elapsed")
    }

    private fun forcePass1Coefficient(profile: String, coefficient: String): String {
        val line = "assign pass1coefficient $coefficient"
        val regex = Regex("(?m)^\\s*assign\\s+pass1coefficient\\s+(=\\s*)?[-0-9.]+.*$")
        return if (regex.containsMatchIn(profile)) {
            profile.replace(regex, line)
        } else {
            profile.replaceFirst(Regex("(?m)^---context:global.*$"), "$0\n$line")
        }
    }
}
