# Routing Hot Path Performance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reduce Beerouter hot-path allocation and per-expansion overhead while preserving the cleaner coordinate abstraction where it is not performance-critical.

**Architecture:** Keep `Position` as the boundary/domain representation, but stop using it as the transport object inside the routing and mapaccess inner loops. Use primitive `longitude`, `latitude`, and encoded `Short` elevation in hot paths, and add primitive overloads to shared geo helpers so implementation stays centralized instead of duplicating coordinate math.

**Tech Stack:** Kotlin Multiplatform, JVM 21, kotlinx benchmark, Kotlin test, existing Beerouter parity tests against BRouter.

---

## File Structure

**Performance harness and verification**
- Modify `benchmark/src/commonMain/kotlin/BeerouterRoutingBenchmark.kt` only if the existing benchmark cannot express the target case cleanly.
- Prefer adding a focused JVM runner under `benchmark/src/jvmMain/kotlin/SingleRoutePerformance.kt` rather than putting temporary profiling entry points in `core`.
- Use the existing public `.rd5` tiles and `misc/profiles2/gravel.brf` with a matching `lookups.dat` for repeatable route-9 `250 km`, `c=1.5` checks.

**Coordinate and elevation representation**
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/Position.kt`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/coordinates.kt`.
- Modify tests in `core/src/jvmTest/kotlin/dev/skynomads/beerouter/map/CoordinateConversionTest.kt`.

**Primitive geo helper overloads**
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/CheapRuler.kt`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/CheapAngleMeter.kt`.

**Mapaccess hot paths**
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmPos.kt`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmNode.kt`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/NodesCache.kt`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/GeometryDecoder.kt`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmTransferNode.kt`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/DirectWeaver.kt`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/WaypointMatcher.kt` and its implementation.

**Router hot paths**
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`.
- Modify code that currently uses `position.altitudeMeters` to use encoded elevation directly.

**Expression cache overhead**
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt`.
- Modify or replace `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/CacheNode.kt` only if a focused cache helper keeps the code simpler than private nested key classes.

---

## Task 1: Establish a Clean Baseline Harness

**Files:**
- Create: `benchmark/src/jvmMain/kotlin/dev/skynomads/beerouter/benchmark/SingleRoutePerformance.kt`
- Modify: `benchmark/build.gradle.kts`

- [ ] **Step 1: Add a single-route runner for route 9, coefficient 1.5**

Create `benchmark/src/jvmMain/kotlin/dev/skynomads/beerouter/benchmark/SingleRoutePerformance.kt`:

```kotlin
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
    public fun main(args: Array<String>): Unit {
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
        println("links_processed=${engine.totalLinksProcessed}")
        engine.routingMetrics.operations
            .filter { it.name == "pass1" || it.name == "pass2" || it.name == "re-tracking" }
            .forEach { op -> println("${op.name},time_ms=${op.elapsedMillis},expansions=${op.expansions}") }
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
```

- [ ] **Step 2: Add a Gradle task for the runner**

Add to `benchmark/build.gradle.kts` after the existing `benchmark { ... }` block:

```kotlin
tasks.register<JavaExec>("singleRoutePerformance") {
    description = "Runs route 9 gravel c=1.5 for focused performance checks"
    group = "verification"
    dependsOn("jvmMainClasses")
    mainClass.set("dev.skynomads.beerouter.benchmark.SingleRoutePerformance")
    val jvmCompilation = kotlin.targets.getByName("jvm").compilations.getByName("main")
    classpath = files(jvmCompilation.output.allOutputs, jvmCompilation.runtimeDependencyFiles)
    project.findProperty("profileArgs")?.toString()?.split(' ')?.let { args = it }
    project.findProperty("jfrFile")?.toString()?.let { jfrFile ->
        jvmArgs("-XX:StartFlightRecording=filename=$jfrFile,settings=profile,dumponexit=true")
    }
}
```

- [ ] **Step 3: Run the baseline three times**

Run:

```bash
podman run --rm \
  -v /home/jgillich/Downloads/beerouter-latest:/work:Z \
  -v /home/jgillich/Downloads/brouter:/brouter:Z \
  -w /work docker.io/library/eclipse-temurin:21-jdk \
  ./gradlew :benchmark:singleRoutePerformance \
  -PprofileArgs="/brouter/tmp-segments /brouter/misc/profiles2 gravel"
```

Expected: prints `distance_m=350191`, non-zero `links_processed`, and pass rows for `pass1`, `pass2`, and `re-tracking`.

- [ ] **Step 4: Record baseline numbers in the implementation notes**

Create `docs/superpowers/plans/2026-04-27-routing-hot-path-performance-results.md` with this header:

```markdown
# Routing Hot Path Performance Results

## Baseline

Command: `./gradlew :benchmark:singleRoutePerformance -PprofileArgs="/brouter/tmp-segments /brouter/misc/profiles2 gravel"`

| Run | elapsed_ms | links_processed | pass1_ms | pass1_expansions | pass2_ms | pass2_expansions |
|---:|---:|---:|---:|---:|---:|---:|
```

Append one table row per run using the actual values printed by the three baseline commands. Do not add guessed values.

---

## Task 2: Remove Nullable and Double Altitude From `Position`

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/Position.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/coordinates.kt`
- Modify: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/map/CoordinateConversionTest.kt`

- [ ] **Step 1: Update tests for primitive encoded elevation**

Replace the altitude tests in `CoordinateConversionTest.kt` with:

```kotlin
@Test
fun testPositionStoresEncodedElevationAsPrimitiveShort() {
    val position = Position.fromDegrees(12.0, 34.0, encodeAltitudeMeters(123.25))

    assertEquals(493.toShort(), position.altitude)
    assertEquals(Short.MIN_VALUE, Position(12.0, 34.0).altitude)
}

@Test
fun testUnsetElevationUsesSentinel() {
    val position = Position(12.0, 34.0)

    assertEquals(Short.MIN_VALUE, position.altitude)
}

@Test
fun testOutOfRangeAltitudeIsClampedInsteadOfWrapping() {
    val high = encodeAltitudeMeters(8849.0)
    val low = encodeAltitudeMeters(-9000.0)

    assertEquals(Short.MAX_VALUE, high)
    assertEquals((Short.MIN_VALUE + 1).toShort(), low)
}
```

- [ ] **Step 2: Run the coordinate tests and verify they fail**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.map.CoordinateConversionTest
```

Expected: fails because `encodeAltitudeMeters` and `Position.fromDegrees(..., Short)` do not exist and old `Double?` altitude API is still present.

- [ ] **Step 3: Replace `Position` altitude with primitive `Short`**

Change `Position.kt` to:

```kotlin
package dev.skynomads.beerouter.geo

private const val COORDINATE_SCALE = 1e-6
private const val LONGITUDE_OFFSET = 180.0
private const val LATITUDE_OFFSET = 90.0
private const val COORDINATE_FACTOR = 1_000_000.0

public data class Position(
    public val longitude: Int,
    public val latitude: Int,
    public val altitude: Short = UNSET_ELEVATION,
) {
    public val id: Long = computeId(longitude, latitude)

    public constructor(longitude: Double, latitude: Double) : this(
        longitude = encodeCoordinate(longitude, LONGITUDE_OFFSET),
        latitude = encodeCoordinate(latitude, LATITUDE_OFFSET),
        altitude = UNSET_ELEVATION,
    )

    public val longitudeDegree: Double
        get() = longitude * COORDINATE_SCALE - LONGITUDE_OFFSET

    public val latitudeDegree: Double
        get() = latitude * COORDINATE_SCALE - LATITUDE_OFFSET

    public companion object {
        public val ZERO: Position = Position(longitude = 0, latitude = 0)

        public fun computeId(longitude: Int, latitude: Int): Long =
            (longitude.toLong() shl 32) or (latitude.toLong() and 0xFFFFFFFFL)

        public fun fromDegrees(longitude: Double, latitude: Double, altitude: Short = UNSET_ELEVATION): Position =
            Position(
                longitude = encodeCoordinate(longitude, LONGITUDE_OFFSET),
                latitude = encodeCoordinate(latitude, LATITUDE_OFFSET),
                altitude = altitude,
            )

        private fun encodeCoordinate(value: Double, offset: Double): Int =
            ((value + offset) * COORDINATE_FACTOR + 0.5).toInt()
    }
}

public const val UNSET_ELEVATION: Short = Short.MIN_VALUE

public fun Long.longitudeFromId(): Int = (this shr 32).toInt()

public fun Long.latitudeFromId(): Int = (this and 0xffffffffL).toInt()

public fun Long.toPosition(): Position = Position(longitudeFromId(), latitudeFromId())
```

- [ ] **Step 4: Replace elevation conversion helpers**

Change `coordinates.kt` to:

```kotlin
package dev.skynomads.beerouter.geo

import kotlin.math.roundToInt

private const val COORDINATE_SCALE = 1e-6
private const val LONGITUDE_OFFSET = 180.0
private const val LATITUDE_OFFSET = 90.0
private const val ELEVATION_SCALE = 4.0
private const val MIN_ENCODED_ELEVATION: Int = Short.MIN_VALUE.toInt() + 1
private const val MAX_ENCODED_ELEVATION: Int = Short.MAX_VALUE.toInt()

public fun Double.toIntLongitude(): Int = ((this + LONGITUDE_OFFSET) / COORDINATE_SCALE).roundToInt()

public fun Double.toIntLatitude(): Int = ((this + LATITUDE_OFFSET) / COORDINATE_SCALE).roundToInt()

public fun Int.toDoubleLongitude(): Double = (this * COORDINATE_SCALE) - LONGITUDE_OFFSET

public fun Int.toDoubleLatitude(): Double = (this * COORDINATE_SCALE) - LATITUDE_OFFSET

public fun encodeAltitudeMeters(altitudeMeters: Double): Short =
    if (altitudeMeters.isNaN()) {
        UNSET_ELEVATION
    } else {
        altitudeMeters.times(ELEVATION_SCALE)
            .toInt()
            .coerceIn(MIN_ENCODED_ELEVATION, MAX_ENCODED_ELEVATION)
            .toShort()
    }

public fun encodedAltitudeToMeters(altitude: Short): Double = altitude / ELEVATION_SCALE
```

- [ ] **Step 5: Fix compilation sites explicitly**

Replace these patterns across `core/src/commonMain/kotlin`:

```kotlin
toSElev(position.altitude)
```

with:

```kotlin
position.altitude
```

Replace:

```kotlin
position.copy(altitude = value.takeUnless { it == Short.MIN_VALUE })
```

with:

```kotlin
position.copy(altitude = value)
```

Replace:

```kotlin
Position(lon, lat, ele.toDouble() / 4.0)
```

with:

```kotlin
Position(lon, lat, ele)
```

Replace non-null external altitude construction:

```kotlin
Position.from(lon, lat, altitudeMeters)
```

with:

```kotlin
Position.fromDegrees(lon, lat, encodeAltitudeMeters(altitudeMeters))
```

- [ ] **Step 6: Run focused tests**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.map.CoordinateConversionTest --tests dev.skynomads.beerouter.router.RoutingTest.routingParityMiniTest
```

Expected: tests pass.

---

## Task 3: Add Primitive Geo Helper Overloads

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/CheapRuler.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/CheapAngleMeter.kt`
- Create: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/geo/PrimitiveGeoHelperTest.kt`

- [ ] **Step 1: Write tests that primitive overloads match `Position` overloads**

Create `PrimitiveGeoHelperTest.kt`:

```kotlin
package dev.skynomads.beerouter.geo

import kotlin.test.Test
import kotlin.test.assertEquals

class PrimitiveGeoHelperTest {
    @Test
    fun primitiveDistanceMatchesPositionDistance() {
        val a = Position(187000000, 139000000)
        val b = Position(190000000, 139200000)

        assertEquals(
            CheapRuler.distance(a, b),
            CheapRuler.distance(a.longitude, a.latitude, b.longitude, b.latitude),
            0.000001,
        )
    }

    @Test
    fun primitiveTurnAngleMatchesPositionTurnAngle() {
        val a = Position(187000000, 139000000)
        val b = Position(188000000, 139100000)
        val c = Position(190000000, 139200000)

        val expected = CheapAngleMeter.turnAngle(a, b, c)
        val actual = CheapAngleMeter.turnAngle(
            a.longitude,
            a.latitude,
            b.longitude,
            b.latitude,
            c.longitude,
            c.latitude,
        )

        assertEquals(expected.angle, actual.angle, 0.000001)
        assertEquals(expected.cosAngle, actual.cosAngle, 0.000001)
    }
}
```

- [ ] **Step 2: Run tests and verify they fail**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.geo.PrimitiveGeoHelperTest
```

Expected: fails because primitive overloads do not exist.

- [ ] **Step 3: Add primitive overloads to `CheapRuler`**

Add to `CheapRuler.kt`:

```kotlin
public object CheapRuler {
    public const val DEG_TO_RAD: Double = kotlin.math.PI / 180.0

    public fun distance(lon1: Int, lat1: Int, lon2: Int, lat2: Int): Double {
        val idx = cacheIndex((lat1 + lat2) shr 1)
        val dlon = (lon1 - lon2) * kxCache[idx]
        val dlat = (lat1 - lat2) * kyCache[idx]
        return sqrt(dlat * dlat + dlon * dlon)
    }

    public fun distance(p1: Position, p2: Position): Double =
        distance(p1.longitude, p1.latitude, p2.longitude, p2.latitude)

    public fun destination(originLon: Int, originLat: Int, distance: Double, angle: Double): Position {
        val idx = cacheIndex(originLat)
        val radAngle = (90.0 - angle) * DEG_TO_RAD
        val longitude = (0.5 + originLon + cos(radAngle) * distance / kxCache[idx]).toInt()
        val latitude = (0.5 + originLat + sin(radAngle) * distance / kyCache[idx]).toInt()
        return Position(longitude, latitude)
    }

    public fun destination(origin: Position, distance: Double, angle: Double): Position =
        destination(origin.longitude, origin.latitude, distance, angle)
}
```

- [ ] **Step 4: Add primitive overloads to `CheapAngleMeter`**

Change `AngleMeasurement` to public if tests need access:

```kotlin
public data class AngleMeasurement(
    public val angle: Double,
    public val cosAngle: Double,
)
```

Add primitive overloads:

```kotlin
public object CheapAngleMeter {
    private const val RAD_TO_DEG = 180.0 / PI

    public fun turnAngle(
        lon0: Int,
        lat0: Int,
        lon1: Int,
        lat1: Int,
        lon2: Int,
        lat2: Int,
    ): AngleMeasurement {
        val idx = cacheIndex(lat1)
        val lon2m = kxCache[idx]
        val lat2m = kyCache[idx]
        val dx10 = (lon1 - lon0) * lon2m
        val dy10 = (lat1 - lat0) * lat2m
        val dx21 = (lon2 - lon1) * lon2m
        val dy21 = (lat2 - lat1) * lat2m
        val dd = sqrt((dx10 * dx10 + dy10 * dy10) * (dx21 * dx21 + dy21 * dy21))
        if (dd == 0.0) return AngleMeasurement(angle = 0.0, cosAngle = 1.0)
        val ddInv = 1.0 / dd
        var sinp = (dy10 * dx21 - dx10 * dy21) * ddInv
        val cosp = (dy10 * dy21 + dx10 * dx21) * ddInv
        var offset = 0.0
        var s2 = sinp * sinp
        if (s2 > 0.5) {
            if (sinp > 0.0) {
                offset = 90.0
                sinp = -cosp
            } else {
                offset = -90.0
                sinp = cosp
            }
            s2 = cosp * cosp
        } else if (cosp < 0.0) {
            sinp = -sinp
            offset = if (sinp > 0.0) -180.0 else 180.0
        }
        return AngleMeasurement(
            angle = offset + sinp * (57.4539 + s2 * (9.57565 + s2 * (4.30904 + s2 * 2.56491))),
            cosAngle = cosp,
        )
    }

    public fun turnAngle(p0: Position, p1: Position, p2: Position): AngleMeasurement =
        turnAngle(p0.longitude, p0.latitude, p1.longitude, p1.latitude, p2.longitude, p2.latitude)

    public fun rawBearing(startLon: Int, startLat: Int, endLon: Int, endLat: Int): Double {
        val xdiff = (endLat - startLat).toDouble()
        val ydiff = (endLon - startLon).toDouble()
        return atan2(ydiff, xdiff) * RAD_TO_DEG
    }

    public fun rawBearing(start: Position, end: Position): Double =
        rawBearing(start.longitude, start.latitude, end.longitude, end.latitude)

    public fun bearing(p1: Position, p2: Position): Double = normalizeAngle(rawBearing(p1, p2))

    public fun measureAngle(lon0: Int, lat0: Int, lon1: Int, lat1: Int, lon2: Int, lat2: Int): Double =
        turnAngle(lon0, lat0, lon1, lat1, lon2, lat2).angle
}
```

- [ ] **Step 5: Run focused tests**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.geo.PrimitiveGeoHelperTest
```

Expected: tests pass.

---

## Task 4: Remove `Position` Construction From `NodesCache` and `OsmNode` Hot Paths

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmNode.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/NodesCache.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/WaypointMatcher.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/WaypointMatcherImpl.kt`

- [ ] **Step 1: Add primitive waypoint matcher methods**

Change `WaypointMatcher.kt` to:

```kotlin
package dev.skynomads.beerouter.codec

import dev.skynomads.beerouter.geo.Position

public interface WaypointMatcher {
    public fun start(startLon: Int, startLat: Int, targetLon: Int, targetLat: Int, useAsStartWay: Boolean): Boolean

    public fun transferNode(lon: Int, lat: Int)


    public fun end()

    public fun hasMatch(lon: Int, lat: Int): Boolean

    public fun start(start: Position, target: Position, useAsStartWay: Boolean): Boolean =
        start(start.longitude, start.latitude, target.longitude, target.latitude, useAsStartWay)

    public fun transferNode(position: Position): Unit = transferNode(position.longitude, position.latitude)

    public fun hasMatch(position: Position): Boolean = hasMatch(position.longitude, position.latitude)
}
```

Update `WaypointMatcherImpl` by moving the existing logic from `Position` methods into the primitive methods and letting the inherited default methods handle `Position` callers.

- [ ] **Step 2: Add primitive `NodesCache` overloads**

In `NodesCache.kt`, change these methods to use primitive overloads internally:

```kotlin
public fun loadSegmentFor(position: Position): Int = loadSegmentFor(position.longitude, position.latitude)

public fun loadSegmentFor(lon: Int, lat: Int): Int {
    val mc = getSegmentFor(lon, lat)
    return mc?.size ?: 0
}

public fun getSegmentFor(position: Position): MicroCache? = getSegmentFor(position.longitude, position.latitude)

public fun getSegmentFor(lon: Int, lat: Int): MicroCache? {
    try {
        val lonDegree = lon / 1000000
        val latDegree = lat / 1000000
        var osmf: OsmFile? = if (lonDegree == lastLonDegree && latDegree == lastLatDegree) lastOsmFile else null
        if (osmf == null) {
            osmf = fileGrid[latDegree][lonDegree]
            if (osmf == null) {
                val fileRow = fileRows[latDegree]
                val ndegrees = fileRow?.size ?: 0
                osmf = fileForSegment(lonDegree, latDegree)
                val newFileRow = arrayOfNulls<OsmFile>(ndegrees + 1)
                for (i in 0..<ndegrees) newFileRow[i] = requireNotNull(fileRow)[i]
                newFileRow[ndegrees] = osmf
                fileRows[latDegree] = newFileRow
                fileGrid[latDegree][lonDegree] = osmf
            }
            lastLonDegree = lonDegree
            lastLatDegree = latDegree
            lastOsmFile = osmf
        }
        currentFileName = osmf.filename
        if (!osmf.hasData()) return null
        var segment = osmf.getMicroCache(lon, lat)
        if (segment == null || (waypointMatcher != null && (waypointMatcher as WaypointMatcherImpl).useDynamicRange)) {
            checkEnableCacheCleaning()
            segment = osmf.createMicroCache(lon, lat, dataBuffers, ctxWay, waypointMatcher, if (directWeaving) nodesMap else null)
            cacheSum += segment.dataSize.toLong()
        } else if (segment.ghost) {
            segment.unGhost()
            ghostWakeup += segment.dataSize.toLong()
        }
        return segment
    } catch (re: RuntimeException) {
        throw re
    } catch (e: Exception) {
        throw RuntimeException("error reading datafile $currentFileName: $e", e)
    }
}
```

This requires corresponding primitive overloads on `OsmFile.getMicroCache(...)` and `OsmFile.createMicroCache(...)`. Add them as delegates to the existing `Position` versions first, then inline the primitive values into the implementation in the same task.

- [ ] **Step 3: Remove `Position` construction from `obtainNonHollowNode`**

Change:

```kotlin
val nodeId = node.idFromPos
val segment = getSegmentFor(Position(nodeId.longitudeFromId(), nodeId.latitudeFromId()))
```

to:

```kotlin
val nodeId = node.idFromPos
val segment = getSegmentFor(nodeId.longitudeFromId(), nodeId.latitudeFromId())
```

- [ ] **Step 4: Use primitive distance in `OsmNode.distanceTo`**

Change:

```kotlin
return max(1, distance(position, p.position).roundToInt())
```

to:

```kotlin
val other = p.position
return max(1, distance(position.longitude, position.latitude, other.longitude, other.latitude).roundToInt())
```

- [ ] **Step 5: Run routing parity mini test**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.RoutingTest.routingParityMiniTest
```

Expected: test passes.

---

## Task 5: Make Geometry Decoding Use Primitive Transfer Nodes

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmTransferNode.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/GeometryDecoder.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt`

- [ ] **Step 1: Replace transfer-node `Position` storage with primitive fields**

Change `OsmTransferNode.kt` to:

```kotlin
package dev.skynomads.beerouter.map

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.geo.UNSET_ELEVATION

public class OsmTransferNode {
    public var next: OsmTransferNode? = null
    public var longitude: Int = 0
    public var latitude: Int = 0
    public var altitude: Short = UNSET_ELEVATION

    public val idFromPos: Long
        get() = Position.computeId(longitude, latitude)

    public fun set(longitude: Int, latitude: Int, altitude: Short): Unit {
        this.longitude = longitude
        this.latitude = latitude
        this.altitude = altitude
    }

    public fun toPosition(): Position = Position(longitude, latitude, altitude)
}
```

- [ ] **Step 2: Update `GeometryDecoder` to reuse transfer nodes without constructing `Position`**

Change the decode loop assignment from:

```kotlin
trans.position = Position(lon, lat, selev.takeUnless { it == Short.MIN_VALUE })
```

to:

```kotlin
trans.set(lon, lat, selev)
```

- [ ] **Step 3: Update `OsmPath` to use primitive transfer-node fields**

Inside `OsmPath.init`, replace transfer-node `Position` reads with primitive values:

```kotlin
val nextLon: Int
val nextLat: Int
val nextEle: Short
val nextId: Long
val nextPositionForRecording: Position?

if (transferNode == null) {
    val targetPosition = requireNotNull(targetNode).position
    nextLon = targetPosition.longitude
    nextLat = targetPosition.latitude
    nextEle = targetPosition.altitude
    nextId = targetPosition.id
    nextPositionForRecording = targetPosition
} else {
    nextLon = transferNode.longitude
    nextLat = transferNode.latitude
    nextEle = transferNode.altitude
    nextId = transferNode.idFromPos
    nextPositionForRecording = null
}
```

Then use primitive helpers:

```kotlin
var dist = rc.distanceBetween(currentPosition.longitude, currentPosition.latitude, nextLon, nextLat)
val angleMeasurement = turnAngle(
    requireNotNull(previousPosition).longitude,
    requireNotNull(previousPosition).latitude,
    currentPosition.longitude,
    currentPosition.latitude,
    nextLon,
    nextLat,
)
```

Only construct `Position(nextLon, nextLat, nextEle)` when `recordTransferNodes` or `message?.wayTags != null` needs a stored path/message position.

- [ ] **Step 4: Run parity mini test and focused benchmark**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.RoutingTest.routingParityMiniTest
```

Expected: test passes.

Run:

```bash
podman run --rm \
  -v /home/jgillich/Downloads/beerouter-latest:/work:Z \
  -v /home/jgillich/Downloads/brouter:/brouter:Z \
  -w /work docker.io/library/eclipse-temurin:21-jdk \
  ./gradlew :benchmark:singleRoutePerformance \
  -PprofileArgs="/brouter/tmp-segments /brouter/misc/profiles2 gravel"
```

Expected: same `distance_m=350191`, lower allocation pressure in follow-up JFR for `Position`.

---

## Task 6: Reduce `DirectWeaver` Position Churn

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/DirectWeaver.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/WaypointMatcher.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/WaypointMatcherImpl.kt`

- [ ] **Step 1: Replace `Position(...).id` with `Position.computeId(...)`**

In `DirectWeaver.kt`, change:

```kotlin
id64Base = Position(lonIdx * cellsize, latIdx * cellsize).id
```

to:

```kotlin
id64Base = Position.computeId(lonIdx * cellsize, latIdx * cellsize)
```

Change turn restriction ID construction from:

```kotlin
val fromId = Position(nodePosition.longitude + bc.decodeNoisyDiff(10), nodePosition.latitude + bc.decodeNoisyDiff(10)).id
val toId = Position(nodePosition.longitude + bc.decodeNoisyDiff(10), nodePosition.latitude + bc.decodeNoisyDiff(10)).id
```

to:

```kotlin
val fromLon = nodePosition.longitude + bc.decodeNoisyDiff(10)
val fromLat = nodePosition.latitude + bc.decodeNoisyDiff(10)
val toLon = nodePosition.longitude + bc.decodeNoisyDiff(10)
val toLat = nodePosition.latitude + bc.decodeNoisyDiff(10)
val fromId = Position.computeId(fromLon, fromLat)
val toId = Position.computeId(toLon, toLat)
```

- [ ] **Step 2: Replace matcher calls with primitive overloads**

Change:

```kotlin
matcher.start(Position(nodePosition.longitude, nodePosition.latitude), Position(ilontarget, ilattarget), useAsStartWay)
matcher?.transferNode(Position(ilontarget - dlonRemaining, ilattarget - dlatRemaining))
```

to:

```kotlin
matcher.start(nodePosition.longitude, nodePosition.latitude, ilontarget, ilattarget, useAsStartWay)
matcher?.transferNode(ilontarget - dlonRemaining, ilattarget - dlatRemaining)
```

- [ ] **Step 3: Run focused benchmark**

Run the same `:benchmark:singleRoutePerformance` command from Task 5.

Expected: same distance and lower `Position` allocation in JFR.

---

## Task 7: Move Hot `OsmNode` Elevation Out of `Position.copy(...)`

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmNode.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmPos.kt`
- Modify call sites reading `node.position.altitude` in `core/src/commonMain/kotlin/dev/skynomads/beerouter/router` and `core/src/commonMain/kotlin/dev/skynomads/beerouter/map`

- [ ] **Step 1: Add primitive elevation to `OsmPos` and `OsmNode`**

Change `OsmPos.kt` to:

```kotlin
public interface OsmPos {
    public val position: Position
    public val altitude: Short
    public fun distanceTo(p: OsmPos): Int
    public val idFromPos: Long
}
```

Change `OsmNode.kt` fields to:

```kotlin
override var position: Position = Position.ZERO
    internal set

override var altitude: Short = UNSET_ELEVATION
    internal set
```

In constructors that accept `Position`, set both fields:

```kotlin
public constructor(position: Position) {
    this.position = Position(position.longitude, position.latitude)
    this.altitude = position.altitude
    _idFromPos = position.id
}
```

- [ ] **Step 2: Stop copying `Position` when parsing elevation**

Change in `parseNodeTags` and `parseNodeBody2`:

```kotlin
position = position.copy(altitude = readSElev)
```

to:

```kotlin
altitude = readSElev
```

Change `clearHollow` and `setHollow` so the hollow marker remains a Boolean and does not overload altitude.

- [ ] **Step 3: Update call sites**

Replace node elevation reads:

```kotlin
node.position.altitude
```

with:

```kotlin
node.altitude
```

When creating a stored track point from a node, use:

```kotlin
Position(node.position.longitude, node.position.latitude, node.altitude)
```

- [ ] **Step 4: Run focused tests**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.RoutingTest.routingParityMiniTest --tests dev.skynomads.beerouter.router.EnergyParityTest
```

Expected: tests pass.

---

## Task 8: Reuse DFS Frames in `OsmNodesMap`

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmNodesMap.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/map/OsmNodesMapTest.kt`

- [ ] **Step 1: Add a regression test for cleanup behavior**

Add to `OsmNodesMapTest.kt` a test that builds a graph with a peninsula and verifies cleanup removes it while preserving a path with an escape. Use existing test helpers in that file; if no helper exists, add this minimal local helper:

```kotlin
private fun node(lon: Int, lat: Int): OsmNode = OsmNode(Position(lon, lat)).apply { clearHollow() }
```

Add test:

```kotlin
@Test
fun cleanupPeninsulasRemovesDeadSubtree() {
    val map = OsmNodesMap()
    map.cleanupMode = OsmNodesMap.CleanupMode.PENINSULAS
    val a = node(180000000, 90000000)
    val b = node(180000100, 90000000)
    val c = node(180000200, 90000000)
    val linkAB = OsmLink()
    val linkBC = OsmLink()
    a.addLink(linkAB, false, b)
    b.addLink(linkBC, false, c)
    a.visitID = 1
    b.visitID = 0
    c.visitID = 0

    map.cleanupAndCount(listOf(a, b, c))

    kotlin.test.assertTrue(a.firstlink == null || a.firstlink!!.isLinkUnused)
}
```

- [ ] **Step 2: Run the test and verify current behavior**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.map.OsmNodesMapTest.cleanupPeninsulasRemovesDeadSubtree
```

Expected: test passes before the refactor, proving behavior is captured.

- [ ] **Step 3: Replace per-push frame allocation with a reusable frame pool**

Change `DfsFrame` from immutable constructor fields to mutable fields with a reset method:

```kotlin
private class DfsFrame {
    var source: OsmNode? = null
    lateinit var n: OsmNode
    var l: OsmLink? = null
    var minId: Int = 0
    var pendingLink: OsmLink? = null
    var pendingTarget: OsmNode? = null
    var pendingNodesCreated: Int = 0

    fun reset(source: OsmNode?, n: OsmNode, minId: Int): DfsFrame {
        this.source = source
        this.n = n
        this.l = n.firstlink
        this.minId = minId
        this.pendingLink = null
        this.pendingTarget = null
        this.pendingNodesCreated = 0
        return this
    }
}
```

In `minVisitIdInSubtree`, replace `ArrayDeque<DfsFrame>()` allocation of new frames with:

```kotlin
val stack = ArrayList<DfsFrame>(256)
var depth = 0

fun frameAt(index: Int): DfsFrame {
    while (stack.size <= index) stack.add(DfsFrame())
    return stack[index]
}

fun push(src: OsmNode?, node: OsmNode) {
    val visitId = if (node.visitID == 1) baseID else lastVisitID++
    node.visitID = visitId
    nodesCreated++
    frameAt(depth++).reset(src, node, visitId)
}
```

Use `stack[depth - 1]` as the current frame and decrement `depth` instead of `stack.removeLast()`.

- [ ] **Step 4: Run cleanup and routing tests**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.map.OsmNodesMapTest --tests dev.skynomads.beerouter.router.RoutingTest.routingParityMiniTest
```

Expected: tests pass and JFR no longer reports `OsmNodesMap$DfsFrame` as significant allocation pressure after this task.

---

## Task 9: Replace Expression Cache Key Allocations With Reusable Probe Keys

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt`
- Modify: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/expressions/EncodeDecodeTest.kt` or create `BExpressionContextCacheTest.kt`

- [ ] **Step 1: Add a cache-key correctness test**

Create `core/src/jvmTest/kotlin/dev/skynomads/beerouter/expressions/BExpressionContextCacheTest.kt`:

```kotlin
package dev.skynomads.beerouter.expressions

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class BExpressionContextCacheTest {
    @Test
    fun unifyReturnsCachedArrayForEqualContent() {
        val meta = BExpressionMetaData()
        val ctx = BExpressionContextWay(16, meta)
        val first = byteArrayOf(1, 2, 3, 4)
        val second = byteArrayOf(0, 1, 2, 3, 4, 9)

        val cached = ctx.unify(first, 0, first.size)!!
        val hit = ctx.unify(second, 1, 4)!!

        assertContentEquals(first, cached)
        assertSame(cached, hit)
        assertNotSame(second, hit)
    }
}
```

- [ ] **Step 2: Run the test and verify current behavior**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.expressions.BExpressionContextCacheTest
```

Expected: test passes before refactor, proving behavior is captured.

- [ ] **Step 3: Replace `ByteArrayKey` data class with reusable key classes**

Replace `ByteArrayKey` in `BExpressionContext.kt` with:

```kotlin
private sealed class ByteArrayKeyBase {
    abstract val hash: Int
    abstract fun contentEqualsStored(bytes: ByteArray): Boolean
    override fun hashCode(): Int = hash
}

private class StoredByteArrayKey(
    override val hash: Int,
    val bytes: ByteArray,
) : ByteArrayKeyBase() {
    override fun equals(other: Any?): Boolean {
        val o = other as? ByteArrayKeyBase ?: return false
        return hash == o.hash && o.contentEqualsStored(bytes)
    }

    override fun contentEqualsStored(bytes: ByteArray): Boolean = this.bytes.contentEquals(bytes)
}

private class ProbeByteArrayKey : ByteArrayKeyBase() {
    override var hash: Int = 0
    private var bytes: ByteArray = ByteArray(0)
    private var offset: Int = 0
    private var len: Int = 0

    fun reset(hash: Int, bytes: ByteArray, offset: Int, len: Int): ProbeByteArrayKey {
        this.hash = hash
        this.bytes = bytes
        this.offset = offset
        this.len = len
        return this
    }

    override fun equals(other: Any?): Boolean {
        val stored = other as? StoredByteArrayKey ?: return false
        if (hash != stored.hash || len != stored.bytes.size) return false
        for (i in 0..<len) {
            if (bytes[offset + i] != stored.bytes[i]) return false
        }
        return true
    }

    override fun contentEqualsStored(bytes: ByteArray): Boolean {
        if (len != bytes.size) return false
        for (i in 0..<len) {
            if (this.bytes[offset + i] != bytes[i]) return false
        }
        return true
    }
}
```

Change the cache field:

```kotlin
private var cache: LruCache<ByteArrayKeyBase, CacheNode>? = null
private val byteArrayProbeKey = ProbeByteArrayKey()
```

Change `unify` so it does not copy on cache hit:

```kotlin
override fun unify(ab: ByteArray, offset: Int, len: Int): ByteArray? {
    val hash = crc(ab, offset, len)
    val cache = requireNotNull(cache)
    val cn = cache[byteArrayProbeKey.reset(hash, ab, offset, len)]
    if (cn != null) {
        lastCacheNode = cn
        return cn.ab
    }
    val stored = ByteArray(len)
    ab.copyInto(stored, 0, offset, offset + len)
    return stored
}
```

When storing cache entries, use:

```kotlin
cache.put(StoredByteArrayKey(hash, ab), cn)
```

- [ ] **Step 4: Run expression tests and routing smoke test**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.expressions.BExpressionContextCacheTest --tests dev.skynomads.beerouter.expressions.EncodeDecodeTest --tests dev.skynomads.beerouter.router.RoutingTest.routingParityMiniTest
```

Expected: tests pass and JFR shows lower `BExpressionContext$ByteArrayKey` allocation pressure.

---

## Task 10: Full Verification and Performance Comparison

**Files:**
- Modify: `docs/superpowers/plans/2026-04-27-routing-hot-path-performance-results.md`

- [ ] **Step 1: Run focused tests**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest --tests dev.skynomads.beerouter.map.CoordinateConversionTest --tests dev.skynomads.beerouter.geo.PrimitiveGeoHelperTest --tests dev.skynomads.beerouter.expressions.BExpressionContextCacheTest --tests dev.skynomads.beerouter.router.RoutingTest.routingParityMiniTest
```

Expected: build successful.

- [ ] **Step 2: Run full JVM test suite**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :core:jvmTest
```

Expected: build successful.

- [ ] **Step 3: Run route 9 benchmark three times**

Run the `:benchmark:singleRoutePerformance` command from Task 1 three times.

Expected: `distance_m=350191` on every run. Record elapsed time and pass timings in the results document.

- [ ] **Step 4: Capture JFR allocation profile**

Run:

```bash
podman run --rm \
  -v /home/jgillich/Downloads/beerouter-latest:/work:Z \
  -v /home/jgillich/Downloads/brouter:/brouter:Z \
  -w /work docker.io/library/eclipse-temurin:21-jdk \
  ./gradlew :benchmark:singleRoutePerformance \
  -PprofileArgs="/brouter/tmp-segments /brouter/misc/profiles2 gravel" \
  -PjfrFile=/work/beerouter-route9-after.jfr
```

Then run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -w /work docker.io/library/eclipse-temurin:21-jdk jfr view allocation-by-class beerouter-route9-after.jfr
```

Expected: `Position`, `java.lang.Short`, `OsmNodesMap$DfsFrame`, and `BExpressionContext$ByteArrayKey` are each materially lower than baseline. If one remains high, record the remaining hotspot in the results document and do not claim that issue is fixed.

- [ ] **Step 5: Run whitespace check**

Run:

```bash
git diff --check
```

Expected: no output.

---

## Self-Review

**Spec coverage:** Covers the identified performance issues: `Position` allocation, nullable boxed `Short`, transfer-node allocation, DirectWeaver object churn, DFS frame allocation, and expression-cache key allocation.

**Marker scan:** No implementation step contains unresolved work labels. The results file instructions require actual measured values from the runner output.

**Type consistency:** The plan consistently uses `Position.longitude`, `Position.latitude`, `Position.altitude: Short`, `UNSET_ELEVATION`, `encodeAltitudeMeters`, `encodedAltitudeToMeters`, primitive `distance(...)`, primitive `turnAngle(...)`, and primitive waypoint matcher overloads.

**Scope check:** The plan avoids wholesale reversal to duplicated primitives. It keeps `Position` at boundaries, centralizes coordinate math in geo helpers, and only introduces primitive paths in measured hot areas.
