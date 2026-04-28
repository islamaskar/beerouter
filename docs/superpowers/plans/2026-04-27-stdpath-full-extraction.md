# StdPath Full Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace rejected non-detail `StdModel` candidate allocation with reusable `StdPathCandidate` scratch and persist Std search survivors as `AcceptedPath` records.

**Architecture:** Keep compatibility `OsmPath` code for kinematic, guide-track/detail, direct, and same-segment paths. For normal non-detail Std routing, create `AcceptedPath` start records, evaluate origin/link transitions in `StdPathCandidate`, compare scratch candidates against accepted dominators, snapshot only survivors, and convert accepted parent chains through `PathTraceBuilder`.

**Tech Stack:** Kotlin Multiplatform, Gradle `:core:jvmTest`, existing router tests and benchmark runner.

---

## File Structure

- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AcceptedPath.kt`: make accepted records usable as the temporary `OsmPath` bridge, add accepted state copy helpers, Std dominance, and fail-fast transition methods.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt`: expose common path state to subclasses where needed by `AcceptedPath` and candidate tests.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt`: add full non-detail Std transition evaluation and snapshot helpers.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPath.kt`: expose `StdPath` compatibility state needed to compare candidate evaluation against existing behavior.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt`: add accepted parent-chain conversion.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`: switch non-detail Std startup, open-set, link-holder, inner-loop, dominance, and final trace branches to `AcceptedPath`/`StdPathCandidate`.
- Modify `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`: add small Std accepted/candidate helpers if needed.
- Test files: `AcceptedPathTest.kt`, `StdPathCandidateTest.kt`, `PathTraceBuilderTest.kt`, `RoutingTest.kt`, `StdPathReuseTest.kt`.

---

### Task 1: Make AcceptedPath An OsmPath Bridge

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AcceptedPath.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/AcceptedPathTest.kt`

- [ ] **Step 1: Write the failing bridge test**

Add this test to `AcceptedPathTest`:

```kotlin
@Test
fun acceptedPathBehavesAsSearchPathBridgeButDoesNotEvaluateTransitions() {
    val source = OsmNode(Position(1, 2, 8))
    val target = OsmNode(Position(3, 4, 12))
    val link = OsmLink(source, target)
    val parent = AcceptedPath(targetNode = source, cost = 7, treedepth = 1)
    val path = AcceptedPath(
        parent = parent,
        sourceNode = source,
        targetNode = target,
        link = link,
        cost = 20,
        airdistance = 30,
        treedepth = 2,
        originPosition = Position(5, 6, 8),
        selev = 9,
        stdState = StdPathState(ehbd = 1, ehbu = 2, totalTime = 3.0, totalEnergy = 4.0),
    )

    assertEquals(20, path.cost)
    assertEquals(30, path.airdistance)
    assertSame(source, path.sourceNode)
    assertSame(target, path.targetNode)
    assertSame(link, path.link)
    assertSame(parent, path.parent)
    assertEquals(4.0, path.totalEnergy)
    assertFailsWith<UnsupportedOperationException> {
        path.init(parent, link, null, false, routingContextFromFiles(profilePath("fastbike.brf"), requireNotNull(testSegmentFile.parent)))
    }
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.AcceptedPathTest.acceptedPathBehavesAsSearchPathBridgeButDoesNotEvaluateTransitions
```

Expected: compile failure because `AcceptedPath` is not an `OsmPath` and has no matching `init` override.

- [ ] **Step 3: Implement the minimal bridge**

Change `AcceptedPath` to extend `OsmPath` and initialize inherited mutable fields in `init { ... }`. Keep constructor properties for `parent` and `stdState`; assign inherited `sourceNode`, `targetNode`, `cost`, `airdistance`, `treedepth`, `originPosition`, and `selev` from constructor values.

Add overrides:

```kotlin
override fun init(orig: OsmPath?) {
    throw UnsupportedOperationException("AcceptedPath does not evaluate transitions")
}

override fun resetState() {
    throw UnsupportedOperationException("AcceptedPath is not recycled")
}

override fun processWaySection(...): Double {
    throw UnsupportedOperationException("AcceptedPath does not evaluate transitions")
}

override fun processTargetNode(rc: RoutingContext): Double {
    throw UnsupportedOperationException("AcceptedPath does not evaluate transitions")
}

override fun elevationCorrection(): Int = stdState.elevationCorrection()

override fun definitlyWorseThan(p: OsmPath?): Boolean = definitlyWorseThanAccepted(p as AcceptedPath)

override val totalTime: Double get() = stdState.totalTime
override val totalEnergy: Double get() = stdState.totalEnergy
```

Add `StdPathState.elevationCorrection()` as an internal function if needed so both `StdPath` and `AcceptedPath` can share the formula.

- [ ] **Step 4: Run the bridge test and existing AcceptedPath test**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.AcceptedPathTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```bash
git diff --check core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AcceptedPath.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathState.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/AcceptedPathTest.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AcceptedPath.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathState.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/AcceptedPathTest.kt
git commit -m "refactor: make accepted path searchable"
```

---

### Task 2: Add AcceptedPath Trace Conversion

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/PathTraceBuilderTest.kt`

- [ ] **Step 1: Write the failing accepted-chain conversion test**

Add this test to `PathTraceBuilderTest`:

```kotlin
@Test
fun buildsFinalTrackElementFromAcceptedPathParentChain() {
    val root = AcceptedPath(
        targetNode = OsmNode(Position(1, 2, 8)),
        cost = 10,
        stdState = StdPathState(totalTime = 1.5, totalEnergy = 2.5),
    )
    val child = AcceptedPath(
        parent = root,
        sourceNode = root.targetNode,
        targetNode = OsmNode(Position(3, 4, 12)),
        cost = 30,
        stdState = StdPathState(totalTime = 4.5, totalEnergy = 6.5),
    )

    val element = PathTraceBuilder.toFinalTrackElement(child)

    assertEquals(Position(3, 4, 12), element.position)
    assertEquals(30, element.cost)
    assertEquals(4.5, element.time)
    assertEquals(6.5, element.energy)
    assertEquals(Position(1, 2, 8), element.origin?.position)
    assertEquals(10, element.origin?.cost)
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.PathTraceBuilderTest.buildsFinalTrackElementFromAcceptedPathParentChain
```

Expected: failure because `PathTraceBuilder` still uses compatibility `originElement` state and does not walk `AcceptedPath.parent`.

- [ ] **Step 3: Implement accepted conversion**

In `PathTraceBuilder`, dispatch `toFinalTrackElement(path: OsmPath)` and `toMatchPathElement(path: OsmPath)` to an accepted-path converter when `path is AcceptedPath`.

The accepted converter should recursively build an `OsmPathElement` from `path.parent`, use `path.targetNode!!.positionWithAltitude()` for position, set `cost`, `time = path.totalTime`, `energy = path.totalEnergy`, and copy `message` if present.

Keep `toDetourElement(path)` compatibility-only for now because detours remain detail-mode compatibility paths.

- [ ] **Step 4: Run trace tests**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.PathTraceBuilderTest --tests dev.skynomads.beerouter.router.OsmPathElementMaterializationTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```bash
git diff --check core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/PathTraceBuilderTest.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/PathTraceBuilder.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/PathTraceBuilderTest.kt
git commit -m "refactor: convert accepted path traces"
```

---

### Task 3: Extract StdPathState Helpers

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathState.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPath.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathStateTest.kt`

- [ ] **Step 1: Write the failing state helper test**

Add this test to `StdPathStateTest`:

```kotlin
@Test
fun stdPathStateComputesElevationCorrectionLikeStdPath() {
    val state = StdPathState(ehbd = 100, ehbu = 200, downhillcostdiv = 10, uphillcostdiv = 20)

    assertEquals(20, state.elevationCorrection())
}
```

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathStateTest.stdPathStateComputesElevationCorrectionLikeStdPath
```

Expected: compile failure because `StdPathState.elevationCorrection()` does not exist.

- [ ] **Step 3: Implement helper and use it from StdPath**

Add to `StdPathState.kt`:

```kotlin
internal fun StdPathState.elevationCorrection(): Int =
    (if (downhillcostdiv > 0) ehbd / downhillcostdiv else 0) +
        (if (uphillcostdiv > 0) ehbu / uphillcostdiv else 0)
```

Change `StdPath.elevationCorrection()` to `return exportStdState().elevationCorrection()`.

- [ ] **Step 4: Run state tests**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathStateTest --tests dev.skynomads.beerouter.router.StdPathReuseTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```bash
git diff --check core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathState.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPath.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathStateTest.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathState.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPath.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathStateTest.kt
git commit -m "refactor: share std elevation correction"
```

---

### Task 4: Add StdPathCandidate Non-Detail Evaluation

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPath.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathCandidateTest.kt`

- [ ] **Step 1: Write the failing compatibility comparison test**

Add this test to `StdPathCandidateTest`:

```kotlin
@Test
fun candidateEvaluationMatchesCompatibilityStdPathForSimpleLink() {
    val rc = routingContextFromFiles(profilePath("fastbike.brf"), requireNotNull(testSegmentFile.parent))
    val source = OsmNode(Position(90000000, 485000000, 100))
    val target = OsmNode(Position(90000100, 485000100, 104))
    val link = OsmLink(source, target).also { it.descriptionBitmap = byteArrayOf(0) }
    val origin = StdPath().apply {
        init(OsmLink(null, source))
        cost = 17
    }
    val acceptedOrigin = AcceptedPath(
        targetNode = source,
        link = origin.link,
        cost = origin.cost,
        selev = origin.selev,
        stdState = origin.exportStdState(),
    )

    val compatibility = rc.createPath(origin, link, null, false) as StdPath
    val candidate = StdPathCandidate()

    val accepted = rc.evaluateStdCandidate(candidate, acceptedOrigin, source, target, link, null)

    assertEquals(compatibility.cost, accepted?.cost)
    assertEquals(compatibility.selev, accepted?.selev)
    assertEquals(compatibility.originPosition, accepted?.originPosition)
    assertEquals(compatibility.exportStdState(), accepted?.stdState)
    rc.recyclePath(compatibility)
}
```

If `byteArrayOf(0)` is not a valid way description for this fixture, replace the link setup in the test with the first non-null `OsmLink.descriptionBitmap` discovered from a generated fixture node. Do not change production code to satisfy an invalid synthetic link.

- [ ] **Step 2: Run the test and verify RED**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathCandidateTest.candidateEvaluationMatchesCompatibilityStdPathForSimpleLink
```

Expected: compile failure because `evaluateStdCandidate` and candidate evaluation do not exist.

- [ ] **Step 3: Implement non-detail evaluation**

Add `RoutingContext.evaluateStdCandidate(candidate, origin, sourceNode, targetNode, link, refTrack): AcceptedPath?` as a transaction wrapper around `candidate.evaluate(...)`; return `candidate.snapshotAccepted()` only when evaluation succeeds.

In `StdPathCandidate.evaluate(...)`, copy the non-detail logic from `OsmPath.addAddionalPenalty()` and `StdPath.processWaySection()` exactly enough to support `detailMode = false`:

- Direct link `descriptionBitmap == null` returns a direct-segment message only if needed by final output; otherwise preserve current cost behavior.
- Way evaluation uses `rc.way.evaluate(rc.inverseDirection xor link.isReverse(sourceNode), description)`.
- Classifier/destination/turn-restriction/nogo/endpoint/target-node/elevation penalties preserve current numeric order and casts.
- `message` stays `null` in non-detail evaluation.
- `stdState.totalTime` and `stdState.totalEnergy` remain unchanged for non-detail, matching `StdPath.computeKinematic()` early return.

Keep compatibility `StdPath` untouched for detail and kinematic paths.

- [ ] **Step 4: Run candidate and evaluation-state tests**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathCandidateTest --tests dev.skynomads.beerouter.router.RoutingEvaluationStateTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

Run:

```bash
git diff --check core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPath.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathCandidateTest.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPath.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPath.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathCandidateTest.kt
git commit -m "refactor: evaluate std candidates in scratch"
```

---

### Task 5: Switch Non-Detail Std Search To AcceptedPath Storage

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingTest.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathReuseTest.kt`

- [ ] **Step 1: Write the failing route-storage test**

Add this test to `StdPathReuseTest`:

```kotlin
@Test
fun stdModelAcceptedSnapshotsIncreaseOnRouteGuard() {
    val rc = routingContextFromFiles(profilePath("trekking.brf"), generatedTestSegmentDir)
    val model = rc.pm as StdModel
    val engine = RoutingEngine(rc)

    val track = kotlinx.coroutines.runBlocking {
        engine.doRouting(
            listOf(
                RoutingTest().createWaypoint("Tübingen A", 48.520272, 9.053658),
                RoutingTest().createWaypoint("Tübingen B", 48.520189, 9.053500),
            )
        )
    }

    assertEquals(15, requireNotNull(track).distance)
    assertTrue(model.acceptedPathSnapshots > 0)
}
```

If direct construction of `RoutingTest().createWaypoint` is awkward, duplicate the two waypoint construction lines in this test using `OsmNodeNamed` and `Position`.

- [ ] **Step 2: Run the test and verify RED or baseline behavior**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathReuseTest.stdModelAcceptedSnapshotsIncreaseOnRouteGuard
```

Expected: pass may already occur because Task 9 introduced the temporary counter. If it passes immediately, replace the assertion with a stronger one that proves no compatibility `StdPath` candidate allocations happened, such as a new `StdModel.createdCompatibilityPaths` counter that remains zero during the non-detail Std branch. Re-run and verify RED.

- [ ] **Step 3: Implement accepted storage switch**

In `RoutingEngine.findTrack`, compute a local `useAcceptedStdSearch = guideTrack == null && routingContext.isStdModel()` after `detailed` is known.

For `useAcceptedStdSearch`:

- Convert start paths from compatibility `StdPath` to `AcceptedPath` before adding them to `openSet` and start link holders.
- Keep open-set type as `SortedHeap<OsmPath?>` while `AcceptedPath` extends `OsmPath`.
- Cast current path/link holders to `AcceptedPath` only inside this branch.
- Use two `StdPathCandidate` instances per candidate link: `testCandidate` and `bestCandidate`. Evaluate each origin/link transition with `RoutingContext.evaluateStdCandidate(...)`; rejected transitions allocate nothing.
- For local best selection, swap candidate scratch references rather than snapshotting each temporary best.
- Compare candidate scratch against accepted dominators using `candidate.definitlyWorseThan(dominator as AcceptedPath)`.
- Snapshot only when the candidate survives radius/cutoff/dominance; add the resulting `AcceptedPath` to the link holder and open set.
- Leave the existing compatibility branch unchanged for guide-track/detail and kinematic routes.

- [ ] **Step 4: Run focused routing tests**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.StdPathReuseTest --tests dev.skynomads.beerouter.router.RoutingTest.generatedTrekkingAdjacentRouteRemainsStable --tests dev.skynomads.beerouter.router.StdPathCandidateTest --tests dev.skynomads.beerouter.router.PathTraceBuilderTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Run route-9 safety benchmark**

Run:

```bash
podman run --rm -v /home/jgillich/Downloads/beerouter-latest:/work:Z -v /home/jgillich/Downloads/brouter:/brouter:Z -w /work docker.io/library/eclipse-temurin:21-jdk ./gradlew :benchmark:singleRoutePerformance -PprofileArgs="/brouter/tmp-segments /brouter/misc/profiles2 gravel" -PjfrFile=/work/beerouter-route9-full-extraction.jfr
```

Expected: output includes `distance_m=350191`. The generated `beerouter-route9-full-extraction.jfr` must remain untracked.

- [ ] **Step 6: Commit**

Run:

```bash
git diff --check core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingTest.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathReuseTest.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt core/src/commonMain/kotlin/dev/skynomads/beerouter/router/StdPathCandidate.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingTest.kt core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/StdPathReuseTest.kt
git commit -m "refactor: store std accepted paths"
```

---

### Task 6: Safety Checkpoint

**Files:**
- No production files expected.

- [ ] **Step 1: Run the focused safety suite**

Run:

```bash
./gradlew :core:jvmTest --tests dev.skynomads.beerouter.router.RoutingEvaluationStateTest --tests dev.skynomads.beerouter.router.PathTraceBuilderTest --tests dev.skynomads.beerouter.router.StdPathStateTest --tests dev.skynomads.beerouter.router.AcceptedPathTest --tests dev.skynomads.beerouter.router.StdPathCandidateTest --tests dev.skynomads.beerouter.router.StdPathReuseTest --tests dev.skynomads.beerouter.router.OsmPathElementMaterializationTest --tests dev.skynomads.beerouter.router.OsmPathElementTest --tests dev.skynomads.beerouter.router.RoutingTest.generatedTrekkingAdjacentRouteRemainsStable --tests dev.skynomads.beerouter.map.OsmNodeTest --tests dev.skynomads.beerouter.map.OsmNodesMapTest --tests dev.skynomads.beerouter.codec.TagValueCoderTest --tests dev.skynomads.beerouter.geo.PositionTest
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run whitespace and status checks**

Run:

```bash
git diff --check
git status --short
```

Expected: `git diff --check` has no output. `git status --short` shows no tracked changes; untracked JFR/CSV profiling artifacts may remain.

- [ ] **Step 3: Optional broad suite comparison**

Run only if the focused safety suite passes:

```bash
./gradlew :core:jvmTest
```

Expected: the broad suite may still show the known seven post-codec parity failures from the previous checkpoint. If new failures appear, use systematic debugging and compare against the previous commit before fixing.

- [ ] **Step 4: Report completion**

Summarize commits, focused verification output, route-9 distance, broad-suite status, and remaining untracked profiling artifacts.
