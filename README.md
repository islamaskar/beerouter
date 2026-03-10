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

## Generating rd5 files

Beerouter can generate `.rd5` segment files with the `generator` Gradle tasks.

### Full planet

```bash
./gradlew :generator:generatePlanetRd5
```

By default this downloads `planet-latest.osm.pbf` from `planet.openstreetmap.org` and writes output
under `generator/build/planet/`.

### Single country or region

```bash
./gradlew :generator:generateCountryRd5 -Pgenerator.country=europe/germany
```

This downloads a Geofabrik extract and runs the same rd5 generation pipeline against it.

`generator.country` is the Geofabrik extract path. Examples:

- `europe/germany`
- `europe/france`
- `north-america/us/california`

### Including POIs

POIs are configured with a simple text file of exact `key=value` matches:

```text
amenity=toilets
amenity=drinking_water
tourism=information
```

Then pass it to either generator task:

```bash
./gradlew :generator:generateCountryRd5 \
  -Pgenerator.country=europe/germany \
  -Pgenerator.poiConfig=/path/to/pois.conf
```

or:

```bash
./gradlew :generator:generatePlanetRd5 \
  -Pgenerator.poiConfig=/path/to/pois.conf
```

POI-enabled builds generate a lookup file that includes those POI tags and copy it into the output
directory as `lookups.dat`.

Important:

- use the generated `lookups.dat` from the rd5 output directory when reading those rd5 files
- do not mix POI-enabled rd5 files with the base `misc/profiles2/lookups.dat`

### Useful properties

Both generation tasks support these optional properties:

- `-Pgenerator.outputDir=/path/to/segments4`
- `-Pgenerator.workDir=/path/to/work`
- `-Pgenerator.profileDir=/path/to/profiles2`
- `-Pgenerator.poiConfig=/path/to/pois.conf`
- `-Pgenerator.srtmDir=/path/to/srtm_bef`
- `-Pgenerator.srtmFallbackDir=/path/to/fallback_srtm_bef`
- `-Pgenerator.dbTagInfo=/path/to/db-tags.txt`

## Basic usage

Artifacts are published to Maven Central as `dev.skynomads.beerouter:beerouter`.

The example below follows the same setup used in
`core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingTest.kt`: create a
`RoutingContext`, optionally configure turn instructions, instantiate `RoutingEngine`, and call
`doRouting()` with named waypoints.

```kotlin
import kotlinx.io.readString
import org.maplibre.spatialk.geojson.Position

private fun waypoint(name: String, lat: Double, lon: Double): OsmNodeNamed =
    OsmNodeNamed(Position(lon, lat)).apply { this.name = name }

fun main() = runBlocking {
    val profile = Path("misc/profiles2/trekking.brf")
    val lookups = Path("misc/profiles2/lookups.dat")
    val segmentDir = Path("/path/to/segments4")
    val profileContent = SystemFileSystem.source(profile).buffered().use { it.readString() }
    val lookupContent = SystemFileSystem.source(lookups).buffered().use { it.readString() }

    val routingContext = RoutingContext(
        profileContent = profileContent,
        lookupContent = lookupContent,
        mapSource = DefaultMapSource(segmentDir)
    )

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

## Publishing

Beerouter publishes the `core` Kotlin Multiplatform library to Maven Central through the Sonatype
Central Portal.

1. Create a Central Portal account and generate a publishing token.
2. Create or reuse a GPG key, then export the ASCII-armored private key with
   `gpg --list-secret-keys --keyid-format LONG` and make sure `gpg` can use it locally.
3. Add the Central Portal token credentials to `~/.gradle/gradle.properties` for local releases:

```properties
mavenCentralUsername=<central-portal-token-username>
mavenCentralPassword=<central-portal-token-password>
```

4. Publish a release with `./gradlew :core:publishAndReleaseToMavenCentral`.

Gradle is configured with `useGpgCmd()`, so when signing runs it uses your local `gpg` setup and
will prompt through GPG pinentry if your key is locked.

On Codeberg CI, pushing a tag that starts with `v` runs `.forgejo/workflows/publish.yml`. Configure
these repository secrets first:

- `MAVEN_CENTRAL_USERNAME`
- `MAVEN_CENTRAL_PASSWORD`
- `MAVEN_GPG_PRIVATE_KEY`
- `MAVEN_GPG_KEY_ID`
- `MAVEN_GPG_PASSPHRASE`
