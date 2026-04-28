# StdPath Accepted-Path Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the `StdPath` search model with explicit candidate scratch, accepted path records, and output-only `OsmPathElement` trace conversion.

**Architecture:** First isolate mutable context and trace conversion while preserving existing `OsmPath` behavior. Then introduce accepted path records and candidate scratch for `StdModel`, keeping kinematic routing on the existing `OsmPath` implementation until the accepted/candidate interfaces have dedicated kinematic support.

**Tech Stack:** Kotlin Multiplatform JVM tests, Gradle, Beerouter core routing, JFR route-9 allocation profiling.

---

## File Structure

- Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEvaluationState.kt`: snapshot and restore candidate-mutated `RoutingContext` fields.
- Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt`: convert current paths and, later, accepted paths into `OsmPathElement` chains.
- Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AcceptedPath.kt`: persistent accepted search record for `StdModel`.
- Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathState.kt`: explicit state copied between accepted records and candidate scratch.
- Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt`: reusable scratch evaluator for `StdModel` transitions.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`: expose snapshot/restore and introduce candidate evaluation entry points.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`: route trace conversion through `PathTraceBuilder`, then switch `StdModel` candidate creation to scratch/snapshot.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt`: keep compatibility during migration, then remove search-time trace materialization after `PathTraceBuilder` owns conversions.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdModel.kt`: own accepted path and candidate creation for standard routing.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPathModel.kt`: add standard-model-specific hooks without forcing `KinematicModel` migration.
- Test `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingEvaluationStateTest.kt`.
- Test `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/PathTraceBuilderTest.kt`.
- Test `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathCandidateTest.kt`.
- Extend `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathReuseTest.kt`.
- Re-run existing route/path tests listed in each task.

## Implementation Tasks

### Task 1: Add Transactional RoutingEvaluationState

**Files:**
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEvaluationState.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingEvaluationStateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingEvaluationStateTest.kt`:

```kotlin
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.MapSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RoutingEvaluationStateTest {
    @Test
    fun restoreReturnsCandidateMutatedRoutingContextFields() {
        val rc = RoutingContextTestFactory.create(MapSource.RD5)
        val originalNogoPoints = mutableListOf(OsmNodeNamed(Position(1, 2)))
        val pending = OsmNodeNamed(Position(3, 4))
        val shortest = Position(5, 6)
        rc.nogopoints = originalNogoPoints
        rc.nogoCost = 12.5
        rc.isEndpoint = true
        rc.shortestmatch = true
        rc.wayfraction = 0.75
        rc.shortestPosition = shortest
        rc.setWaypoint(OsmNodeNamed(Position(7, 8)), pending, false)

        val snapshot = rc.snapshotEvaluationState()

        rc.nogopoints = mutableListOf(OsmNodeNamed(Position(9, 10)))
        rc.nogoCost = -1.0
        rc.isEndpoint = false
        rc.shortestmatch = false
        rc.wayfraction = 0.25
        rc.shortestPosition = Position(11, 12)
        assertTrue(rc.checkPendingEndpoint())

        rc.restoreEvaluationState(snapshot)

        assertSame(originalNogoPoints, rc.nogopoints)
        assertEquals(12.5, rc.nogoCost)
        assertTrue(rc.isEndpoint)
        assertTrue(rc.shortestmatch)
        assertEquals(0.75, rc.wayfraction)
        assertEquals(shortest, rc.shortestPosition)
        assertTrue(rc.checkPendingEndpoint())
        rc.unsetWaypoint()
        assertFalse(rc.isEndpoint)
        assertNull(rc.shortestPosition)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.RoutingEvaluationStateTest.restoreReturnsCandidateMutatedRoutingContextFields`

Expected: compile failure with unresolved `snapshotEvaluationState` and `restoreEvaluationState`.

- [ ] **Step 3: Add the snapshot type**

Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEvaluationState.kt`:

```kotlin
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position

public data class RoutingEvaluationState internal constructor(
    internal val nogopoints: MutableList<OsmNodeNamed>,
    internal val keepnogopoints: MutableList<OsmNodeNamed>,
    internal val pendingEndpoint: OsmNodeNamed?,
    internal val nogoCost: Double,
    internal val isEndpoint: Boolean,
    internal val shortestmatch: Boolean,
    internal val wayfraction: Double,
    internal val shortestPosition: Position?,
)
```

- [ ] **Step 4: Add snapshot/restore APIs**

Modify `RoutingContext.kt` near `unsetWaypoint()`:

```kotlin
    public fun snapshotEvaluationState(): RoutingEvaluationState = RoutingEvaluationState(
        nogopoints = nogopoints,
        keepnogopoints = keepnogopoints,
        pendingEndpoint = pendingEndpoint,
        nogoCost = nogoCost,
        isEndpoint = isEndpoint,
        shortestmatch = shortestmatch,
        wayfraction = wayfraction,
        shortestPosition = shortestPosition,
    )

    public fun restoreEvaluationState(state: RoutingEvaluationState) {
        nogopoints = state.nogopoints
        keepnogopoints = state.keepnogopoints
        pendingEndpoint = state.pendingEndpoint
        nogoCost = state.nogoCost
        isEndpoint = state.isEndpoint
        shortestmatch = state.shortestmatch
        wayfraction = state.wayfraction
        shortestPosition = state.shortestPosition
    }
```

Change `pendingEndpoint` and `keepnogopoints` from `private` to `internal` only if the new file cannot access them through the constructor call. Keep the new API as the only external access path.

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.RoutingEvaluationStateTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEvaluationState.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingEvaluationStateTest.kt
git commit -m "refactor: snapshot routing evaluation state"
```

### Task 2: Extract Current Trace Conversion Into PathTraceBuilder

**Files:**
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/PathTraceBuilderTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/PathTraceBuilderTest.kt`:

```kotlin
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.OsmNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PathTraceBuilderTest {
    @Test
    fun buildsTerminalElementFromCurrentOsmPathCompatibilityPath() {
        val origin = StdPath().apply {
            targetNode = OsmNode(Position(1, 2, 12))
            cost = 10
        }
        val child = StdPath().apply {
            targetNode = OsmNode(Position(3, 4, 20))
            cost = 30
            message = MessageData().also { it.linkdist = 7 }
            deferOriginElement(origin)
        }

        val element = PathTraceBuilder.toPathElement(child)

        assertEquals(Position(3, 4, 20), element.position)
        assertEquals(30, element.cost)
        assertEquals(7, element.message?.linkdist)
        assertEquals(Position(1, 2, 12), element.origin?.position)
        assertEquals(10, element.origin?.cost)
        assertSame(origin.myElement, element.origin)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.PathTraceBuilderTest.buildsTerminalElementFromCurrentOsmPathCompatibilityPath`

Expected: compile failure with unresolved `PathTraceBuilder`.

- [ ] **Step 3: Add the compatibility trace builder**

Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt`:

```kotlin
package dev.skynomads.beerouter.router

internal object PathTraceBuilder {
    fun toPathElement(path: OsmPath): OsmPathElement {
        path.materializeOriginElement()
        val target = requireNotNull(path.targetNode)
        return OsmPathElement(
            target.positionWithAltitude(),
            path.originElement
        ).also {
            it.cost = path.cost
            it.message = path.message
        }
    }
}
```

- [ ] **Step 4: Route existing conversion through the builder**

Modify `RoutingEngine.kt` private extension `OsmPath.toPathElement()`:

```kotlin
    private fun OsmPath.toPathElement(): OsmPathElement = PathTraceBuilder.toPathElement(this)
```

- [ ] **Step 5: Run focused tests**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.PathTraceBuilderTest --tests dev.skynomads.beerouter.router.OsmPathElementMaterializationTest --tests dev.skynomads.beerouter.router.StdPathReuseTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/PathTraceBuilderTest.kt
git commit -m "refactor: centralize path trace conversion"
```

### Task 3: Add StdPathState Copy Boundary

**Files:**
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathState.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPath.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathStateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathStateTest.kt`:

```kotlin
package dev.skynomads.beerouter.router

import kotlin.test.Test
import kotlin.test.assertEquals

class StdPathStateTest {
    @Test
    fun stdPathStateCanRoundTripThroughCompatibilityPath() {
        val source = StdPath()
        source.totalTime = 12.5
        source.totalEnergy = 99.25
        source.importStdState(StdPathState(ehbd = 1, ehbu = 2, totalTime = 12.5, totalEnergy = 99.25, elevationBuffer = 3.5f, uphillcostdiv = 4, downhillcostdiv = 5))

        val state = source.exportStdState()

        assertEquals(1, state.ehbd)
        assertEquals(2, state.ehbu)
        assertEquals(12.5, state.totalTime)
        assertEquals(99.25, state.totalEnergy)
        assertEquals(3.5f, state.elevationBuffer)
        assertEquals(4, state.uphillcostdiv)
        assertEquals(5, state.downhillcostdiv)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathStateTest.stdPathStateCanRoundTripThroughCompatibilityPath`

Expected: compile failure with unresolved `StdPathState`, `importStdState`, and `exportStdState`.

- [ ] **Step 3: Add explicit StdPathState**

Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathState.kt`:

```kotlin
package dev.skynomads.beerouter.router

internal data class StdPathState(
    var ehbd: Int = 0,
    var ehbu: Int = 0,
    var totalTime: Double = 0.0,
    var totalEnergy: Double = 0.0,
    var elevationBuffer: Float = 0f,
    var uphillcostdiv: Int = 0,
    var downhillcostdiv: Int = 0,
)
```

- [ ] **Step 4: Add compatibility import/export on StdPath**

Modify `StdPath.kt`:

```kotlin
    internal fun exportStdState(): StdPathState = StdPathState(
        ehbd = ehbd,
        ehbu = ehbu,
        totalTime = totalTime,
        totalEnergy = totalEnergy,
        elevationBuffer = elevation_buffer,
        uphillcostdiv = uphillcostdiv,
        downhillcostdiv = downhillcostdiv,
    )

    internal fun importStdState(state: StdPathState) {
        ehbd = state.ehbd
        ehbu = state.ehbu
        totalTime = state.totalTime
        totalEnergy = state.totalEnergy
        elevation_buffer = state.elevationBuffer
        uphillcostdiv = state.uphillcostdiv
        downhillcostdiv = state.downhillcostdiv
    }
```

Place these methods above `processWaySection()`.

- [ ] **Step 5: Run focused tests**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathStateTest --tests dev.skynomads.beerouter.router.StdPathReuseTest`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathState.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPath.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathStateTest.kt
git commit -m "refactor: extract std path state"
```

### Task 4: Introduce AcceptedPath Compatibility Record

**Files:**
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AcceptedPath.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/AcceptedPathTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/AcceptedPathTest.kt`:

```kotlin
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.OsmLink
import dev.skynomads.beerouter.map.OsmNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class AcceptedPathTest {
    @Test
    fun acceptedPathStoresPersistentSearchStateAndLinkHolderPointer() {
        val parent = AcceptedPath(targetNode = OsmNode(Position(1, 2)), cost = 10)
        val source = OsmNode(Position(1, 2))
        val target = OsmNode(Position(3, 4))
        val link = OsmLink(source, target)
        val state = StdPathState(ehbd = 1, ehbu = 2, totalTime = 3.0, totalEnergy = 4.0)

        val path = AcceptedPath(
            parent = parent,
            sourceNode = source,
            targetNode = target,
            link = link,
            cost = 20,
            airdistance = 30,
            treedepth = 2,
            originPosition = Position(5, 6),
            selev = 7,
            stdState = state,
        )

        assertSame(parent, path.parent)
        assertSame(source, path.sourceNode)
        assertSame(target, path.targetNode)
        assertSame(link, path.link)
        assertEquals(20, path.cost)
        assertEquals(30, path.airdistance)
        assertEquals(2, path.treedepth)
        assertEquals(Position(5, 6), path.originPosition)
        assertEquals(7.toShort(), path.selev)
        assertEquals(state, path.stdState)
        assertNull(path.nextForLink)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.AcceptedPathTest.acceptedPathStoresPersistentSearchStateAndLinkHolderPointer`

Expected: compile failure with unresolved `AcceptedPath`.

- [ ] **Step 3: Add AcceptedPath**

Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AcceptedPath.kt`:

```kotlin
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.OsmLink
import dev.skynomads.beerouter.map.OsmLinkHolder
import dev.skynomads.beerouter.map.OsmNode

internal class AcceptedPath(
    val parent: AcceptedPath? = null,
    val sourceNode: OsmNode? = null,
    val targetNode: OsmNode? = null,
    val link: OsmLink? = null,
    var cost: Int = 0,
    var airdistance: Int = 0,
    var treedepth: Int = 0,
    val originPosition: Position? = null,
    val selev: Short = 0,
    val stdState: StdPathState = StdPathState(),
) : OsmLinkHolder {
    override var nextForLink: OsmLinkHolder? = null
}
```

- [ ] **Step 4: Run focused test**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.AcceptedPathTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AcceptedPath.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/AcceptedPathTest.kt
git commit -m "refactor: add accepted path record"
```

### Task 5: Add StdPathCandidate Scratch And Snapshot

**Files:**
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathCandidateTest.kt`

- [ ] **Step 1: Write the failing test**

Create `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathCandidateTest.kt`:

```kotlin
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.OsmLink
import dev.skynomads.beerouter.map.OsmNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class StdPathCandidateTest {
    @Test
    fun candidateSnapshotsIntoAcceptedPathWithoutSharingMutableStateObject() {
        val parent = AcceptedPath(targetNode = OsmNode(Position(1, 2)), cost = 10)
        val source = OsmNode(Position(1, 2))
        val target = OsmNode(Position(3, 4))
        val link = OsmLink(source, target)
        val candidate = StdPathCandidate()

        candidate.resetFrom(parent, source, target, link)
        candidate.cost = 25
        candidate.airdistance = 40
        candidate.treedepth = 3
        candidate.selev = 8
        candidate.originPosition = Position(5, 6)
        candidate.stdState.ehbd = 11

        val accepted = candidate.snapshotAccepted()
        candidate.stdState.ehbd = 99

        assertSame(parent, accepted.parent)
        assertSame(source, accepted.sourceNode)
        assertSame(target, accepted.targetNode)
        assertSame(link, accepted.link)
        assertEquals(25, accepted.cost)
        assertEquals(40, accepted.airdistance)
        assertEquals(3, accepted.treedepth)
        assertEquals(8.toShort(), accepted.selev)
        assertEquals(Position(5, 6), accepted.originPosition)
        assertEquals(11, accepted.stdState.ehbd)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathCandidateTest.candidateSnapshotsIntoAcceptedPathWithoutSharingMutableStateObject`

Expected: compile failure with unresolved `StdPathCandidate`.

- [ ] **Step 3: Add candidate scratch**

Create `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt`:

```kotlin
package dev.skynomads.beerouter.router

import dev.skynomads.beerouter.geo.Position
import dev.skynomads.beerouter.map.OsmLink
import dev.skynomads.beerouter.map.OsmNode

internal class StdPathCandidate {
    private var parent: AcceptedPath? = null
    private var sourceNode: OsmNode? = null
    private var targetNode: OsmNode? = null
    private var link: OsmLink? = null
    var cost: Int = 0
    var airdistance: Int = 0
    var treedepth: Int = 0
    var originPosition: Position? = null
    var selev: Short = 0
    val stdState: StdPathState = StdPathState()

    fun resetFrom(parent: AcceptedPath, sourceNode: OsmNode, targetNode: OsmNode, link: OsmLink) {
        this.parent = parent
        this.sourceNode = sourceNode
        this.targetNode = targetNode
        this.link = link
        cost = parent.cost
        airdistance = 0
        treedepth = parent.treedepth + 1
        originPosition = parent.originPosition
        selev = parent.selev
        stdState.ehbd = parent.stdState.ehbd
        stdState.ehbu = parent.stdState.ehbu
        stdState.totalTime = parent.stdState.totalTime
        stdState.totalEnergy = parent.stdState.totalEnergy
        stdState.elevationBuffer = parent.stdState.elevationBuffer
        stdState.uphillcostdiv = parent.stdState.uphillcostdiv
        stdState.downhillcostdiv = parent.stdState.downhillcostdiv
    }

    fun snapshotAccepted(): AcceptedPath = AcceptedPath(
        parent = requireNotNull(parent),
        sourceNode = sourceNode,
        targetNode = targetNode,
        link = link,
        cost = cost,
        airdistance = airdistance,
        treedepth = treedepth,
        originPosition = originPosition,
        selev = selev,
        stdState = stdState.copy(),
    )
}
```

- [ ] **Step 4: Run focused test**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathCandidateTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathCandidateTest.kt
git commit -m "refactor: add std path candidate scratch"
```

### Task 6: Add Behavioral Route Guard Before Switching The Search Loop

**Files:**
- Modify: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingTest.kt`

- [ ] **Step 1: Add a focused guard test**

Append to `RoutingTest.kt` a test that uses the existing routing test factory helpers. If `RoutingTest.kt` already has a route-9-style helper, use it directly. Add this exact assertion shape:

```kotlin
    @Test
    fun routeNineGravelPassOneCoefficientOnePointFiveRemainsStable() = runTest {
        val track = routeTestCase(
            fromLon = 10.733333,
            fromLat = 51.000000,
            toLon = 13.426948,
            toLat = 49.553392,
            profile = "gravel",
            keyValues = mapOf("pass1coefficient" to "1.5"),
        )

        assertEquals(350191, track.distance)
    }
```

If the existing test infrastructure uses different helper names, adapt only the helper call, not the asserted behavior.

- [ ] **Step 2: Run test to verify it passes before redesign**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.RoutingTest.routeNineGravelPassOneCoefficientOnePointFiveRemainsStable`

Expected: PASS. If this cannot run because the full route fixture is too expensive or unavailable, replace it with the smallest existing deterministic routing fixture and assert both `track.distance` and `track.nodes.map { it.idFromPos }`.

- [ ] **Step 3: Commit**

```bash
git add core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingTest.kt
git commit -m "test: guard std routing result"
```

### Task 7: Switch Trace Conversion Call Sites To Named Builder Modes

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/PathTraceBuilderTest.kt`

- [ ] **Step 1: Write failing tests for named modes**

Add to `PathTraceBuilderTest.kt`:

```kotlin
    @Test
    fun finalTrackAndMatchPathConversionsUseSameCompatibilityElementForNow() {
        val path = StdPath().apply {
            targetNode = OsmNode(Position(9, 10, 11))
            cost = 44
        }

        assertEquals(PathTraceBuilder.toFinalTrackElement(path).position, PathTraceBuilder.toMatchPathElement(path).position)
        assertEquals(44, PathTraceBuilder.toDetourElement(path).cost)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.PathTraceBuilderTest.finalTrackAndMatchPathConversionsUseSameCompatibilityElementForNow`

Expected: compile failure with unresolved named builder methods.

- [ ] **Step 3: Add named builder modes**

Modify `PathTraceBuilder.kt`:

```kotlin
internal object PathTraceBuilder {
    fun toPathElement(path: OsmPath): OsmPathElement = toFinalTrackElement(path)

    fun toFinalTrackElement(path: OsmPath): OsmPathElement = toCompatibilityElement(path)

    fun toMatchPathElement(path: OsmPath): OsmPathElement = toCompatibilityElement(path)

    fun toDetourElement(path: OsmPath): OsmPathElement = toCompatibilityElement(path)

    private fun toCompatibilityElement(path: OsmPath): OsmPathElement {
        path.materializeOriginElement()
        val target = requireNotNull(path.targetNode)
        return OsmPathElement(
            target.positionWithAltitude(),
            path.originElement
        ).also {
            it.cost = path.cost
            it.message = path.message
        }
    }
}
```

- [ ] **Step 4: Update RoutingEngine call sites**

Change the private extension and explicit detour/match/final conversion call sites:

```kotlin
matchPath = PathTraceBuilder.toMatchPathElement(path)
currentGuideTrack.registerDetourForId(currentNode.idFromPos, PathTraceBuilder.toDetourElement(detour))
var element: OsmPathElement? = PathTraceBuilder.toFinalTrackElement(path)
```

Remove or keep `private fun OsmPath.toPathElement()` only if all call sites are converted. If kept, it must delegate to `PathTraceBuilder.toFinalTrackElement(this)`.

- [ ] **Step 5: Run focused tests**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.PathTraceBuilderTest --tests dev.skynomads.beerouter.router.OsmPathElementMaterializationTest --tests dev.skynomads.beerouter.router.RoutingTest.routeNineGravelPassOneCoefficientOnePointFiveRemainsStable`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/PathTraceBuilderTest.kt
git commit -m "refactor: name path trace conversion modes"
```

### Task 8: Prototype StdModel Candidate Evaluation Behind Existing OsmPath

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdModel.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathCandidateTest.kt`

- [ ] **Step 1: Write failing test for transactional evaluation helper**

Add to `StdPathCandidateTest.kt`:

```kotlin
    @Test
    fun rejectedCompatibilityCandidateRestoresEvaluationState() {
        val rc = RoutingContextTestFactory.create(MapSource.RD5)
        rc.nogoCost = 5.0
        rc.shortestmatch = true
        rc.wayfraction = 0.5
        rc.shortestPosition = Position(7, 8)

        val stateBefore = rc.snapshotEvaluationState()
        rc.evaluateStdCompatibilityCandidate {
            rc.nogoCost = -1.0
            rc.shortestmatch = false
            null
        }

        val stateAfter = rc.snapshotEvaluationState()
        assertEquals(stateBefore, stateAfter)
    }
```

Add imports:

```kotlin
import dev.skynomads.beerouter.map.MapSource
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathCandidateTest.rejectedCompatibilityCandidateRestoresEvaluationState`

Expected: compile failure with unresolved `evaluateStdCompatibilityCandidate`.

- [ ] **Step 3: Add transactional compatibility helper**

Modify `RoutingContext.kt`:

```kotlin
    internal inline fun <T> evaluateStdCompatibilityCandidate(block: () -> T?): T? {
        val state = snapshotEvaluationState()
        val result = block()
        if (result == null) {
            restoreEvaluationState(state)
        }
        return result
    }
```

Place it near `createPath(origin, link, ...)`.

- [ ] **Step 4: Run focused tests**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathCandidateTest --tests dev.skynomads.beerouter.router.RoutingEvaluationStateTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathCandidateTest.kt
git commit -m "refactor: isolate std candidate evaluation state"
```

### Task 9: Switch Rejected Std Candidates To Scratch-First Evaluation

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdModel.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathReuseTest.kt`

- [ ] **Step 1: Add a failing allocation-path guard**

Add to `StdPathReuseTest.kt`:

```kotlin
    @Test
    fun stdModelTracksAcceptedSnapshotsSeparatelyFromRejectedCandidates() {
        val model = StdModel()

        assertEquals(0, model.acceptedPathSnapshots)
        model.recordAcceptedPathSnapshotForTest()
        assertEquals(1, model.acceptedPathSnapshots)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathReuseTest.stdModelTracksAcceptedSnapshotsSeparatelyFromRejectedCandidates`

Expected: compile failure with unresolved `acceptedPathSnapshots` and `recordAcceptedPathSnapshotForTest`.

- [ ] **Step 3: Add snapshot counters behind test-visible internals**

Modify `StdModel.kt`:

```kotlin
    internal var acceptedPathSnapshots: Int = 0
        private set

    internal fun recordAcceptedPathSnapshotForTest() {
        acceptedPathSnapshots++
    }

    internal fun recordAcceptedPathSnapshot() {
        acceptedPathSnapshots++
    }
```

This is a temporary guard for migration; remove it when JFR verifies allocation reduction.

- [ ] **Step 4: Wrap survivor insertion points**

Modify `RoutingEngine.kt` at the single accepted survivor insertion point before `candidateLink.addLinkHolder(bestPath, currentNode)`:

```kotlin
if (routingContext.pm is StdModel) {
    (routingContext.pm as StdModel).recordAcceptedPathSnapshot()
}
```

This does not yet reduce allocations; it marks the exact survivor boundary for the next task.

- [ ] **Step 5: Run focused route/path tests**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathReuseTest --tests dev.skynomads.beerouter.router.RoutingTest.routeNineGravelPassOneCoefficientOnePointFiveRemainsStable`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdModel.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathReuseTest.kt
git commit -m "refactor: mark accepted std path boundary"
```

### Task 10: Replace Local Rejected StdPath Allocation With Candidate Scratch

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt`

- [ ] **Step 1: Add explicit candidate evaluation API**

Modify `RoutingContext.kt`:

```kotlin
    internal fun isStdModel(): Boolean = pm is StdModel
```

This task deliberately keeps full `OsmPath` creation for kinematic and detail modes.

- [ ] **Step 2: Change only non-detail StdModel candidate creation branch**

In `RoutingEngine.kt`, inside the `while (linkHolder != null)` loop where `testPath` is created, introduce this branch:

```kotlin
val testPath = if (currentGuideTrack == null && routingContext.isStdModel()) {
    routingContext.evaluateStdCompatibilityCandidate {
        routingContext.createPath(otherPath, candidateLink, refTrack, false).takeIf { it.cost >= 0 }
    }
} else {
    routingContext.createPath(otherPath, candidateLink, refTrack, currentGuideTrack != null)
}
```

Keep the rest of the logic unchanged. This is an intermediate step that proves transactionality and isolates the non-detail branch before replacing the inner `createPath` with real scratch evaluation.

- [ ] **Step 3: Run focused tests**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.RoutingEvaluationStateTest --tests dev.skynomads.beerouter.router.StdPathCandidateTest --tests dev.skynomads.beerouter.router.RoutingTest.routeNineGravelPassOneCoefficientOnePointFiveRemainsStable`

Expected: PASS.

- [ ] **Step 4: Profile route 9**

Run: `podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -v /home/jgillich/Downloads/brouter:/brouter:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :benchmark:singleRoutePerformance -PprofileArgs="/brouter/tmp-segments /brouter/misc/profiles2 gravel" -PjfrFile=/work/beerouter-route9-accepted-path-phase1.jfr`

Expected: `distance_m=350191`. Allocation should not materially improve yet; this is a safety checkpoint.

- [ ] **Step 5: Commit**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt
git commit -m "refactor: isolate std candidate branch"
```

### Task 11: Stop After Safety Phase And Review


**Files:**
- No code changes unless Task 10 revealed failures.

- [ ] **Step 1: Run full focused verification**

Run: `./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.RoutingEvaluationStateTest --tests dev.skynomads.beerouter.router.PathTraceBuilderTest --tests dev.skynomads.beerouter.router.StdPathStateTest --tests dev.skynomads.beerouter.router.AcceptedPathTest --tests dev.skynomads.beerouter.router.StdPathCandidateTest --tests dev.skynomads.beerouter.router.StdPathReuseTest --tests dev.skynomads.beerouter.router.OsmPathElementMaterializationTest --tests dev.skynomads.beerouter.router.OsmPathElementTest --tests dev.skynomads.beerouter.map.OsmNodeTest --tests dev.skynomads.beerouter.map.OsmNodesMapTest --tests dev.skynomads.beerouter.codec.TagValueCoderTest --tests dev.skynomads.beerouter.geo.PositionTest`

Expected: PASS.

- [ ] **Step 2: Run diff check**

Run: `git diff --check`

Expected: no output.

- [ ] **Step 3: Summarize what remains**

Do not start the final scratch rewrite until this safety phase is reviewed. The next plan revision should replace the compatibility `createPath()` inside the non-detail StdModel branch with a real `StdPathCandidate.evaluate()` that copies `OsmPath.addAddionalPenalty()` logic without allocating `StdPath` for rejected candidates.

## Self-Review

- Spec coverage: The plan covers transactional `RoutingContext`, trace conversion modes, accepted path record, model state extraction, candidate scratch, kinematic boundary, behavior guards, and profiling checkpoints. The final full scratch rewrite is intentionally deferred until the safety phase is reviewed because it requires copying and validating the large `OsmPath.addAddionalPenalty()` path.
- Placeholder scan: No placeholder markers or vague implementation-only instructions remain. Where existing helper names may differ in `RoutingTest.kt`, the plan specifies the exact asserted behavior and replacement rule.
- Type consistency: `RoutingEvaluationState`, `PathTraceBuilder`, `StdPathState`, `AcceptedPath`, and `StdPathCandidate` signatures are introduced before later tasks reference them.
