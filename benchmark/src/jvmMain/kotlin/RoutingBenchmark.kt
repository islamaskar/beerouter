package dev.skynomads.beerouter.benchmark

import dev.skynomads.beerouter.router.OsmNodeNamed
import dev.skynomads.beerouter.router.RoutingContext
import dev.skynomads.beerouter.router.RoutingEngine
import kotlinx.benchmark.*
import kotlinx.coroutines.runBlocking
import org.maplibre.spatialk.geojson.Position
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Duration.Companion.seconds
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

@State(Scope.Benchmark)
@Warmup(iterations = 1)
@Measurement(iterations = 1)
open class RoutingBenchmark {
    val root = File(System.getProperty("user.dir"))

    val segmentFile = File(
        File(System.getProperty("java.io.tmpdir")), "E5_N45.rd5"
    )

    fun createWaypoint(name: String, lat: Double, lon: Double): OsmNodeNamed {
        val n = OsmNodeNamed()
        n.name = name
        n.position = Position(lon, lat)
        return n
    }

    val waypoints = listOf(
        createWaypoint(
            "Tübingen",
            48.5203263,
            9.053596
        ),
        createWaypoint(
            "Horb am Neckar",
            48.4421,
            8.6849
        )
    )

    @Setup
    fun prepare() {
        if (!segmentFile.exists()) {
            val url = URL("https://brouter.de/brouter/segments4/E5_N45.rd5")
            url.openStream()
                .use { Files.copy(it, segmentFile.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        }
    }

    @Benchmark
    fun beerouter() {
        val profile = File(root, "../misc/profiles2/trekking.brf")
        require(profile.exists())
        val lookup = File(profile.parentFile, "lookups.dat")
        require(lookup.exists())

        val ctx = SystemFileSystem.source(Path(profile.path)).buffered().use { profileSource ->
            SystemFileSystem.source(Path(lookup.path)).buffered().use { lookupSource ->
                RoutingContext(
                    profileName = profile.name,
                    segmentDir = Path(segmentFile.parentFile.path),
                    profileSource = profileSource,
                    lookupSource = lookupSource
                ).apply {
                    global.turnInstructionMode = 2
                }
            }
        }

        val engine = RoutingEngine(ctx)

        val track = runBlocking { engine.doRouting(waypoints) }

        require(track != null)
    }

    @Benchmark
    fun brouter() {
        val profile = File(root, "../misc/profiles2/trekking.brf")
        require(profile.exists())

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
                    ilon = it.iLon
                    ilat = it.iLat
                }
            },
            rc
        )

        cr.doRouting(60.seconds.inWholeMilliseconds)

        require(cr.foundTrack != null)
    }
}
