package dev.skynomads.beerouter.benchmark

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.DefaultMapSource
import dev.skynomads.beerouter.router.OsmNodeNamed
import dev.skynomads.beerouter.router.RoutingContext
import dev.skynomads.beerouter.router.RoutingEngine
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
open class PoiPerformanceBenchmark {
    @Param("with-pois", "no-pois")
    lateinit var poiMode: String

    private lateinit var segmentDir: Path
    private lateinit var profile: Path
    private lateinit var lookup: Path

    private val waypoints = listOf(
        createWaypoint("Tübingen", 48.5203263, 9.053596),
        createWaypoint("Ravensburg", 47.7810, 9.6035),
        createWaypoint("Ulm", 48.3974, 9.9936),
    )

    @Setup
    fun prepare() {
        val segmentsBase = System.getProperty("beerouter.routingTestSegmentsBase")
            ?: error("missing system property beerouter.routingTestSegmentsBase")
        val suffix = if (poiMode == "with-pois") "segments4" else "segments4-no-pois"
        segmentDir = Path(segmentsBase, suffix)

        require(SystemFileSystem.exists(segmentDir)) { "missing segment dir: $segmentDir" }

        val profilePath = findRequiredPath("misc/profiles2/trekking.brf", "../misc/profiles2/trekking.brf")
        profile = profilePath
        val segmentLookup = Path(segmentDir, "lookups.dat")
        lookup = if (SystemFileSystem.exists(segmentLookup)) segmentLookup else Path(requireNotNull(profilePath.parent), "lookups.dat")

        require(SystemFileSystem.exists(profile)) { "missing profile: $profile" }
        require(SystemFileSystem.exists(lookup)) { "missing lookup: $lookup" }
    }

    @Benchmark
    fun routeLongDistance() {
        val ctx = SystemFileSystem.source(profile).buffered().use { profileSource ->
            SystemFileSystem.source(lookup).buffered().use { lookupSource ->
                RoutingContext(
                    profileContent = profileSource.readString(),
                    lookupContent = lookupSource.readString(),
                    mapSource = DefaultMapSource(segmentDir)
                )
            }
        }

        val engine = RoutingEngine(ctx)
        val track = runBlocking { engine.doRouting(waypoints) }
        require(track != null) { "routing returned null for poiMode=$poiMode" }
    }

    private fun createWaypoint(name: String, lat: Double, lon: Double): OsmNodeNamed =
        OsmNodeNamed(Position(lon, lat)).apply { this.name = name }

    private fun findRequiredPath(vararg candidates: String): Path {
        for (candidate in candidates) {
            val path = Path(candidate)
            if (SystemFileSystem.exists(path)) return path
        }
        error("none of the candidate files exist: ${candidates.joinToString()}")
    }
}