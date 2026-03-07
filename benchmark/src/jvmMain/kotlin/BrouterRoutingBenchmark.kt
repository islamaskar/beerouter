package dev.skynomads.beerouter.benchmark

import btools.router.OsmNodeNamed
import btools.router.RoutingContext
import btools.router.RoutingEngine
import dev.skynomads.beerouter.osm.toIntLatitude
import dev.skynomads.beerouter.osm.toIntLongitude
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Measurement
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.Warmup
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory

@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
open class BrouterRoutingBenchmark {
    private val segmentDir = SystemTemporaryDirectory
    private val segmentFile = Path(segmentDir, "E5_N45.rd5")
    private val profile = findProfileFile()

    private val waypoints = listOf(
        createWaypoint("Tuebingen", 48.5203263, 9.053596),
        createWaypoint("Horb am Neckar", 48.4421, 8.6849)
    )

    @Setup
    fun prepare() {
        ensureSegmentFile(segmentFile)
        require(SystemFileSystem.exists(profile)) { "missing trekking profile file at $profile" }
    }

    @Benchmark
    fun brouter() {
        val rc = RoutingContext().apply {
            turnInstructionMode = 2
            localFunction = profile.toString()
        }

        val engine = RoutingEngine(
            null,
            null,
            File(segmentDir.toString()),
            waypoints,
            rc
        )
        engine.quite = true
        engine.doRouting(60.seconds.inWholeMilliseconds)

        require(engine.foundTrack != null)
        require(engine.errorMessage == null) { "unexpected benchmark routing error: ${engine.errorMessage}" }
    }

    private fun createWaypoint(name: String, lat: Double, lon: Double): OsmNodeNamed {
        return OsmNodeNamed().apply {
            this.name = name
            this.ilon = lon.toIntLongitude()
            this.ilat = lat.toIntLatitude()
        }
    }

    private fun findProfileFile(): Path {
        val first = Path("misc", "profiles2", "trekking.brf")
        if (SystemFileSystem.exists(first)) return first
        val second = Path("..", "misc", "profiles2", "trekking.brf")
        if (SystemFileSystem.exists(second)) return second
        error("missing trekking profile file")
    }
}
