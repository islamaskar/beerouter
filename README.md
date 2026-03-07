# Beerouter

Beerouter is an offline routing engine for OpenStreetMap data. It started as a fork of
[BRouter](https://brouter.de/) and focuses on modernizing the code base, preserving routing
compatibility, and making the engine available as a Kotlin Multiplatform library.

## Routing inputs

A routing request needs three things:

1. a routing profile such as [`misc/profiles2/trekking.brf`](misc/profiles2/trekking.brf)
2. the matching lookup table [`misc/profiles2/lookups.dat`](misc/profiles2/lookups.dat)
3. a directory containing `.rd5` segment files for the area you want to route in

The JVM tests use the segment file `E5_N45.rd5`, downloaded from
`https://brouter.de/brouter/segments4/`.

## Basic usage

The example below follows the same setup used in
`core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingTest.kt`: create a
`RoutingContext`, optionally configure turn instructions, instantiate `RoutingEngine`, and call
`doRouting()` with named waypoints.

```kotlin
import dev.skynomads.beerouter.mapaccess.DefaultMapAccessFileSystem
import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.router.OsmNodeNamed
import dev.skynomads.beerouter.router.OsmTrack
import dev.skynomads.beerouter.router.RoutingContext
import dev.skynomads.beerouter.router.RoutingEngine
import dev.skynomads.beerouter.router.TurnInstructionMode
import kotlinx.coroutines.runBlocking
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

private fun waypoint(name: String, lat: Double, lon: Double): OsmNodeNamed =
    OsmNodeNamed().apply {
        this.name = name
        position = Position(lon, lat)
    }

fun main() = runBlocking {
    val profile = Path("misc/profiles2/trekking.brf")
    val lookups = Path("misc/profiles2/lookups.dat")
    val segmentDir = Path("/path/to/segments4")

    val routingContext = SystemFileSystem.source(profile).buffered().use { profileSource ->
        SystemFileSystem.source(lookups).buffered().use { lookupSource ->
            RoutingContext(
                profileName = "trekking.brf",
                profileSource = profileSource,
                lookupSource = lookupSource,
                mapAccessFileSystem = DefaultMapAccessFileSystem(segmentDir)
            )
        }
    }.apply {
        global.turnInstructionMode = TurnInstructionMode.LOCUS
    }

    val track: OsmTrack = RoutingEngine(routingContext).doRouting(
        listOf(
            waypoint("Tuebingen", 48.5203263, 9.0535960),
            waypoint("Rottenburg", 48.4769600, 8.9336788)
        )
    ) ?: error("No route found")

    println("Distance: ${track.distance} m")
    println("Ascend: ${track.ascend} m")
    println("Travel time: ${track.totalSeconds} s")
    println("Track points: ${track.nodes.size}")
    println("Voice hints: ${track.voiceHints.size}")
}
```

## Performance

```
Benchmark                    Mode  Cnt   Score   Error  Units
RoutingBenchmark.beerouter  thrpt    5  14.987 ± 1.847  ops/s
RoutingBenchmark.brouter    thrpt    5  15.979 ± 0.815  ops/s
```

Generated with: `be784db87956b17153b3dbdd85e7fcb03dc8c67c`

## License

Beerouter is licensed under the [Mozilla Public License Version 2.0](LICENSE). Its code is derived
from the BRouter project; see [ATTRIBUTION](ATTRIBUTION).
