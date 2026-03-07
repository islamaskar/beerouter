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
import kotlinx.io.files.SystemTemporaryDirectory
import kotlin.test.assertEquals

@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
open class BrouterRoutingBenchmark {
    private val segmentDir = File(SystemTemporaryDirectory.toString())
    private val segmentFile = Path("${segmentDir.path}/E5_N45.rd5")

    private val waypoints = listOf(
        createWaypoint("Tuebingen", 48.5203263, 9.053596),
        createWaypoint("Horb am Neckar", 48.4421, 8.6849)
    )

    @Setup
    fun prepare() {
        ensureSegmentFile(segmentFile)
        require(File("../misc/profiles2/trekking.brf").exists() || File("misc/profiles2/trekking.brf").exists())
    }

    @Benchmark
    fun brouter() {
        val profile = findProfileFile()

        val rc = RoutingContext().apply {
            turnInstructionMode = 2
            localFunction = profile.path
        }

        val engine = RoutingEngine(
            null,
            null,
            segmentDir,
            waypoints,
            rc
        )
        engine.quite = true
        engine.doRouting(60.seconds.inWholeMilliseconds)

        require(engine.foundTrack != null)
        assertEquals(engine.errorMessage, null)
    }

    private fun createWaypoint(name: String, lat: Double, lon: Double): OsmNodeNamed {
        return OsmNodeNamed().apply {
            this.name = name
            this.ilon = lon.toIntLongitude()
            this.ilat = lat.toIntLatitude()
        }
    }

    private fun findProfileFile(): File {
        val first = File("misc/profiles2/trekking.brf")
        if (first.exists()) return first
        val second = File("../misc/profiles2/trekking.brf")
        if (second.exists()) return second
        error("missing trekking profile file")
    }
}
