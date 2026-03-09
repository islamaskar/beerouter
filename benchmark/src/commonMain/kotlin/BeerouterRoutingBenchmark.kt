package dev.skynomads.beerouter.benchmark

import dev.skynomads.beerouter.mapaccess.DefaultMapAccessFileSystem
import dev.skynomads.beerouter.router.OsmNodeNamed
import dev.skynomads.beerouter.router.RoutingContext
import dev.skynomads.beerouter.router.RoutingEngine
import dev.skynomads.beerouter.router.TurnInstructionMode
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.readString
import org.maplibre.spatialk.geojson.Position

@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
open class BeerouterRoutingBenchmark {
    private val segmentDir = if (SystemFileSystem.exists(SystemTemporaryDirectory)) {
        SystemTemporaryDirectory
    } else {
        Path("/tmp")
    }
    private val segmentFile = Path(segmentDir, SEGMENT_FILE_NAME)
    private val profile = findRequiredPath(
        "misc/profiles2/trekking.brf",
        "../misc/profiles2/trekking.brf"
    )
    private val lookup = Path(requireNotNull(profile.parent), "lookups.dat")

    private val waypoints = listOf(
        createWaypoint("Tuebingen", 48.5203263, 9.053596),
        createWaypoint("Horb am Neckar", 48.4421, 8.6849)
    )

    @Setup
    fun prepare() {
        require(SystemFileSystem.exists(profile)) { "missing profile file at $profile" }
        require(SystemFileSystem.exists(lookup)) { "missing lookup file at $lookup" }
        ensureSegmentFile(segmentFile)
    }

    @Benchmark
    fun beerouter() {
        val ctx = SystemFileSystem.source(profile).buffered().use { profileSource ->
            SystemFileSystem.source(lookup).buffered().use { lookupSource ->
                RoutingContext(
                    profileContent = profileSource.readString(),
                    lookupContent = lookupSource.readString(),
                    mapAccessFileSystem = DefaultMapAccessFileSystem(segmentDir)
                ).apply {
                    global.turnInstructionMode = TurnInstructionMode.LOCUS
                }
            }
        }

        val engine = RoutingEngine(ctx)
        val track = runBlocking { engine.doRouting(waypoints) }

        require(track != null)
    }

    private fun createWaypoint(name: String, lat: Double, lon: Double): OsmNodeNamed =
        OsmNodeNamed(Position(lon, lat)).apply { this.name = name }

    private fun findRequiredPath(vararg candidates: String): Path {
        for (candidate in candidates) {
            val path = Path(candidate)
            if (SystemFileSystem.exists(path)) {
                return path
            }
        }
        error("none of the candidate files exist: ${candidates.joinToString()}")
    }
    private companion object {
        private const val SEGMENT_FILE_NAME = "E5_N45.rd5"
    }
}

internal expect fun ensureSegmentFile(segmentFile: Path)
