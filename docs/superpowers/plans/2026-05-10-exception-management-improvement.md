# Exception Management Improvement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking. **DO NOT COMMIT.** Stage changes only.

**Goal:** Improve exception handling in the Beerouter `core` module by introducing domain exceptions, fixing misused standard types, removing catch-and-rethrow wrappers, and adding `@throws` KDoc tags to all public API methods.

**Architecture:** Hybrid approach: two new domain exceptions (`NoRouteFoundException`, `ProfileParseException`) plus correct usage of standard Kotlin exceptions. All changes are localized to throw/catch sites and KDoc headers; no behavioral changes to routing logic.

**Tech Stack:** Kotlin Multiplatform (`commonMain`), kotlinx-io, Gradle

---

### Task 1: Create Domain Exception Types

**Files:**
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/NoRouteFoundException.kt`
- Create: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/ProfileParseException.kt`
- Test: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/ExceptionHierarchyTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
package dev.skynomads.beerouter.router

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull

class ExceptionHierarchyTest {
    @Test
    fun noRouteFoundExceptionIsRuntimeException() {
        val ex = NoRouteFoundException("no track")
        assertTrue(ex is RuntimeException)
        assertEquals("no track", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun profileParseExceptionIsRuntimeException() {
        val cause = IllegalArgumentException("bad token")
        val ex = ProfileParseException("line 5", cause)
        assertTrue(ex is RuntimeException)
        assertEquals("line 5", ex.message)
        assertEquals(cause, ex.cause)
    }

    @Test
    fun profileParseExceptionAcceptsNullCause() {
        val ex = ProfileParseException("line 5")
        assertTrue(ex is RuntimeException)
        assertEquals("line 5", ex.message)
        assertNull(ex.cause)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.router.ExceptionHierarchyTest"`
Expected: FAIL — `NoRouteFoundException` and `ProfileParseException` not found

- [ ] **Step 3: Implement `NoRouteFoundException`**

```kotlin
package dev.skynomads.beerouter.router

/**
 * Thrown when the routing engine cannot find a valid path between waypoints.
 *
 * This replaces the previous `IllegalArgumentException("no track found...")`
 * and similar routing-failure messages so that API consumers can catch
 * routing failures specifically.
 */
public class NoRouteFoundException(message: String) : RuntimeException(message)
```

- [ ] **Step 4: Implement `ProfileParseException`**

```kotlin
package dev.skynomads.beerouter.router

/**
 * Thrown when a routing profile or lookup file cannot be parsed.
 *
 * This replaces the generic `RuntimeException` and `IllegalArgumentException`
 * wrappers used in [BExpressionContext] during profile parsing.
 */
public class ProfileParseException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.router.ExceptionHierarchyTest"`
Expected: PASS

- [ ] **Step 6: Stage changes (DO NOT COMMIT)**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/NoRouteFoundException.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/ProfileParseException.kt
git add core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/ExceptionHierarchyTest.kt
```

---

### Task 2: Remap Throw Sites — Router Package

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt:1494`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt:1818`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt:400`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/SearchBoundary.kt:21` (verify — keep as-is)
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt:155` (narrow catch)
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/BExpression.kt:189`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/BExpression.kt:291`

- [ ] **Step 1: Change `RoutingEngine.kt:1494`**

Find:
```kotlin
throw IllegalArgumentException("no track found at pass=$cfi")
```
Replace with:
```kotlin
throw NoRouteFoundException("no track found at pass=$cfi")
```

- [ ] **Step 2: Change `RoutingEngine.kt:1818`**

Find:
```kotlin
throw IllegalArgumentException("memory limit reached")
```
Replace with:
```kotlin
throw IllegalStateException("memory limit reached")
```

- [ ] **Step 3: Change `RoutingContext.kt:400`**

Find:
```kotlin
else -> throw RuntimeException("Cannot create path-model: unsupported model '$className'")
```
Replace with:
```kotlin
else -> throw IllegalArgumentException("Cannot create path-model: unsupported model '$className'")
```

- [ ] **Step 4: Change `AreaReader.kt:155`**

Find:
```kotlin
} catch (e: Exception) {
    println("AreaReader error after used=$used / count=$count: ${e.message}")
    ais.clear()
}
```
Replace with:
```kotlin
} catch (e: IOException) {
    // I/O failure while scanning area info is non-fatal; clear results and continue.
    println("AreaReader I/O error after used=$used / count=$count: ${e.message}")
    ais.clear()
}
```

Note: Ensure `kotlinx.io.IOException` (or `java.io.IOException` on JVM) is imported at the top of the file. `AreaReader.kt` already imports `kotlinx.io.IOException` via `kotlinx.io.Sink` and `Source`. Verify the import exists.

- [ ] **Step 5: Change `BExpression.kt:189`**

Find:
```kotlin
else throw IllegalArgumentException("unexpected end of file")
```
Replace with:
```kotlin
else throw ProfileParseException("unexpected end of file")
```

- [ ] **Step 6: Change `BExpression.kt:291`**

Find:
```kotlin
throw IllegalArgumentException("unknown expression: $operator")
```
Replace with:
```kotlin
throw ProfileParseException("unknown expression: $operator")
```

- [ ] **Step 7: Run router package tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.router.*"`
Expected: PASS

- [ ] **Step 8: Stage changes (DO NOT COMMIT)**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/BExpression.kt
```

---

### Task 3: Remap Throw Sites — Map / Codec / Expressions Packages

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmNode.kt:138`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmNode.kt:159`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmNode.kt:322`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/NodesCache.kt:202-206`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/TagValueCoder.kt:235`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache2.kt:456`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt:127`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt:452`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt:455`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt:469`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt:731-735`

- [ ] **Step 1: Change `OsmNode.kt:138`**

Find:
```kotlin
if (mc !is MicroCache2) throw IllegalArgumentException("unknown cache version: " + mc::class.simpleName)
```
Replace with:
```kotlin
if (mc !is MicroCache2) throw IllegalStateException("unknown cache version: " + mc::class.simpleName)
```

- [ ] **Step 2: Change `OsmNode.kt:159`**

Find:
```kotlin
} else throw IllegalArgumentException("unknown cache version: " + mc::class.simpleName)
```
Replace with:
```kotlin
} else throw IllegalStateException("unknown cache version: " + mc::class.simpleName)
```

- [ ] **Step 3: Change `OsmNode.kt:322`**

Find:
```kotlin
throw IllegalArgumentException("unlinkLink: unknown source")
```
Replace with:
```kotlin
throw IllegalStateException("unlinkLink: unknown source")
```

- [ ] **Step 4: Fix `NodesCache.kt:202-206`**

Find:
```kotlin
} catch (re: RuntimeException) {
    throw re
} catch (e: Exception) {
    throw RuntimeException("error reading datafile $currentFileName: $e", e)
}
```
Replace with: remove both catch blocks entirely. The enclosing `try` should just end.

Before:
```kotlin
try {
    ...
} catch (re: RuntimeException) {
    throw re
} catch (e: Exception) {
    throw RuntimeException("error reading datafile $currentFileName: $e", e)
}
```
After:
```kotlin
// Removed catch-and-rethrow wrapper; let IOException propagate naturally.
try {
    ...
}
```

- [ ] **Step 5: Change `TagValueCoder.kt:235`**

Find:
```kotlin
throw RuntimeException("identity corruption!")
```
Replace with:
```kotlin
throw IllegalStateException("identity corruption!")
```

- [ ] **Step 6: Change `MicroCache2.kt:456`**

Find:
```kotlin
if (nodeIdx == n) throw RuntimeException("ups: self ref?")
```
Replace with:
```kotlin
if (nodeIdx == n) throw IllegalStateException("ups: self ref?")
```

- [ ] **Step 7: Change `BExpressionContext.kt:127`**

Find:
```kotlin
if (ld2[inum] != ld[inum]) throw RuntimeException(
```
Replace with:
```kotlin
if (ld2[inum] != ld[inum]) throw IllegalStateException(
```

- [ ] **Step 8: Change `BExpressionContext.kt:452`**

Find:
```kotlin
if (nv != nv2) throw RuntimeException("mismatch in variable-count: $nv<->$nv2")
```
Replace with:
```kotlin
if (nv != nv2) throw IllegalStateException("mismatch in variable-count: $nv<->$nv2")
```

- [ ] **Step 9: Change `BExpressionContext.kt:455`**

Find:
```kotlin
throw RuntimeException(
```
Replace with:
```kotlin
throw IllegalStateException(
```

- [ ] **Step 10: Change `BExpressionContext.kt:469`**

Find:
```kotlin
throw RuntimeException("no variable for index$idx")
```
Replace with:
```kotlin
throw IllegalStateException("no variable for index$idx")
```

- [ ] **Step 11: Fix `BExpressionContext.kt:731-735`**

Find:
```kotlin
} catch (e: IllegalArgumentException) {
    throw IllegalArgumentException("ParseException at line $linenr: ${e.message}")
} catch (e: Exception) {
    throw RuntimeException(e)
}
```
Replace with:
```kotlin
} catch (e: IllegalArgumentException) {
    throw ProfileParseException("ParseException at line $linenr: ${e.message}", e)
}
// Removed catch (e: Exception) — do not mask unexpected errors.
```

- [ ] **Step 12: Run map/codec/expressions tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.map.*" --tests "dev.skynomads.beerouter.codec.*" --tests "dev.skynomads.beerouter.expressions.*"`
Expected: PASS

- [ ] **Step 13: Stage changes (DO NOT COMMIT)**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmNode.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/map/NodesCache.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/TagValueCoder.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache2.kt
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt
```

---

### Task 4: Fix Silent Swallowing in `RoutingEngine`

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt:409-416`

- [ ] **Step 1: Narrow catch and add explanatory comment**

Find:
```kotlin
try {
    wpt1.radius = searchRadius * 1.5
    sinkFactory().buffered().use { sink ->
        areareader.writeAreaInfo(sink, wpt1, ais)
    }
} catch (e: Exception) {
    // Silently ignore exceptions
}
```
Replace with:
```kotlin
try {
    wpt1.radius = searchRadius * 1.5
    sinkFactory().buffered().use { sink ->
        areareader.writeAreaInfo(sink, wpt1, ais)
    }
} catch (e: IOException) {
    // Area-info export is best-effort; failure should not abort routing.
}
```

- [ ] **Step 2: Run router tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.router.*"`
Expected: PASS

- [ ] **Step 3: Stage changes (DO NOT COMMIT)**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt
```

---

### Task 5: Add `@throws` Documentation — Router Package

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingEngine.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/RoutingContext.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmTrack.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmNode.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmPathElement.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmNodeNamed.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/OsmNogoPolygon.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AcceptedPath.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/SearchBoundary.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/VoiceHintList.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/router/VoiceHint.kt`

For **every `public` method** in each of these files that may throw (or transitively calls something that throws), add a `@throws` KDoc tag naming the most specific exception type the caller should expect. Follow this pattern:

```kotlin
/**
 * ... existing docs ...
 *
 * @throws NoRouteFoundException if ...
 * @throws IllegalStateException if ...
 * @throws IOException if ...
 */
```

If a method has no existing KDoc, add a minimal one with `@throws`.

Key methods to annotate (non-exhaustive — check all `public` methods):
- `RoutingEngine.doRouting`, `doGetInfo`
- `RoutingContext.setWaypoint`, `createPath`, `createPrePath`, `freeNoWays`, `restoreNogoList`, `cleanNogoList`, `checkMatchedWaypointAgainstNogos`, `allInOneNogo`, `checkPendingEndpoint`, `distanceBetween`
- `OsmTrack.readBinary`, `writeBinary`, `addNodes`, `buildMap`, `prepareSpeedProfile`, `processVoiceHints`
- `OsmNode.parseNodeTags`, `parseNodeBody`, `parseNodeBody2`, `addLink`, `unlinkLink`
- `OsmPathElement.readFromStream`, `writeToStream`
- `OsmNodeNamed.decodeNogo`
- `OsmNogoPolygon.intersects`, `isOnPolyline`, `isWithin`, `distanceWithinPolygon`
- `AreaReader.getDirectAllData`, `getDirectData`, `writeAreaInfo`, `readAreaInfo`
- `AcceptedPath` methods already throwing `UnsupportedOperationException`
- `SearchBoundary.create`
- `VoiceHintList.setTransportMode`, `getTransportMode`
- `VoiceHint.formatGeometry`, `hasGiveWay`

- [ ] **Step 1: Annotate all router package public methods**

Edit each file, adding `@throws` tags to every `public` method that throws or calls throwing code.

- [ ] **Step 2: Run router tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.router.*"`
Expected: PASS (KDoc changes do not affect compilation or tests)

- [ ] **Step 3: Stage changes (DO NOT COMMIT)**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/router/
```

---

### Task 6: Add `@throws` Documentation — Map Package

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/NodesCache.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmFile.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/PhysicalFile.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/OsmNodesMap.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/DirectWeaver.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/WaypointMatcherImpl.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/PoiFinder.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/TurnRestriction.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/GeometryDecoder.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/map/RoutingMemoryPolicy.kt`

For **every `public` method** that may throw, add `@throws` KDoc tags.

Key methods:
- `NodesCache.loadSegmentFor`, `getSegmentFor`, `obtainNonHollowNode`, `expandHollowLinkTargets`, `hasHollowLinkTargets`, `getStartNode`, `getGraphNode`, `matchWaypointsToNodes`, `close`, `getElevationType`
- `OsmFile.getMicroCache`, `createMicroCache`, `createMicroCacheForCell`, `getDataInputForSubIdx`
- `PhysicalFile.checkVersionIntegrity`, `checkFileIntegrity`, `close`
- `OsmNodesMap.cleanupAndCount`, `isInMemoryBounds`, `canEscape`, `clearTemp`, `collectOutreachers`, `get`, `put`, `remove`
- `DirectWeaver.expandId`
- `WaypointMatcherImpl.start`, `transferNode`, `end`, `hasMatch`, `updateWayList`
- `PoiFinder.find`, `close`
- `TurnRestriction.isTurnForbidden`, `create`
- `GeometryDecoder.decodeGeometry`
- `RoutingMemoryPolicy.default`, `withTotalHardLimitMegabytes`

- [ ] **Step 1: Annotate all map package public methods**

- [ ] **Step 2: Run map tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.map.*"`
Expected: PASS

- [ ] **Step 3: Stage changes (DO NOT COMMIT)**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/map/
```

---

### Task 7: Add `@throws` Documentation — Codec Package

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache2.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/LinkedListContainer.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/IntegerFifo3Pass.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/TagValueCoder.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/StatCoderContext.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/NoisyDiffCoder.kt`

For **every `public` method** that may throw, add `@throws` KDoc tags.

Key methods:
- `MicroCache.emptyCache`, `getAndClear`, `finishNode`, `discardNode`, `collect`, `unGhost`, `getIdForIndex`, `compareWith`, `buildDelta`, `addDelta`, `expandId`, `shrinkId`, `isInternal`, `encodeMicroCache`
- `MicroCache2.readUnified`
- `LinkedListContainer.addDataElement`, `initList`
- `IntegerFifo3Pass.add`
- `TagValueCoder.encodeTagValueSet`, `decodeTagValueSet`, `encodeDictionary`
- `StatCoderContext.encodeNoisyNumber`, `decodeNoisyNumber`, `encodeNoisyDiff`, `decodeNoisyDiff`, `encodePredictedValue`, `decodePredictedValue`, `encodeSortedArray`, `decodeSortedArray`
- `NoisyDiffCoder.encodeSignedValue`, `decodeSignedValue`, `encodeDictionary`

- [ ] **Step 1: Annotate all codec package public methods**

- [ ] **Step 2: Run codec tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.codec.*"`
Expected: PASS

- [ ] **Step 3: Stage changes (DO NOT COMMIT)**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/
```

---

### Task 8: Add `@throws` Documentation — Expressions Package

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContextWay.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContextNode.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpression.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionMetaData.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionLookupValue.kt`

For **every `public` method** that may throw, add `@throws` KDoc tags.

Key methods:
- `BExpressionContext.encode`, `decode`, `getKeyValueDescription`, `getMap`, `getLookupKey`, `getLookupValue`, `evaluate`, `createNewLookupData`, `generateRandomValues`, `assertAllVariablesEqual`, `variableName`, `addLookupValue`, `getBooleanLookupValue`, `getOutputVariableIndex`, `setForeignContext`, `getForeignVariableValue`, `getForeignVariableIdx`, `parseProfile`, `setVariableValue`, `getVariableValue`, `getVariableIdx`, `getLookupMatch`, `getLookupNameIdx`, `markLookupIdxUsed`, `isLookupIdxUsed`, `setAllTagsUsed`, `usedTagList`, `getLookupValueIdx`, `parseToken`, `assign`, `checkStartWay`, `freeNoWays`
- `BExpressionContextWay` all public overrides
- `BExpressionContextNode` all public overrides
- `BExpression.parse`
- `BExpressionMetaData.registerListener`, `readMetaData`
- `BExpressionLookupValue.addAlias`, `matches`

- [ ] **Step 1: Annotate all expressions package public methods**

- [ ] **Step 2: Run expressions tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.expressions.*"`
Expected: PASS

- [ ] **Step 3: Stage changes (DO NOT COMMIT)**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/
```

---

### Task 9: Add `@throws` Documentation — Geo and Util Packages

**Files:**
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/PrimitiveGeoHelper.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/Position.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/util/SortedHeap.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/util/MixCoderDataInputStream.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/util/MixCoderDataOutputStream.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/util/ByteDataReader.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/util/ByteDataWriter.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/util/BitCoderContext.kt`
- Modify: `core/src/commonMain/kotlin/dev/skynomads/beerouter/util/ReducedMedianFilter.kt`

For **every `public` method** that may throw, add `@throws` KDoc tags.

- [ ] **Step 1: Annotate all geo/util package public methods**

- [ ] **Step 2: Run geo/util tests**

Run: `./gradlew :core:jvmTest --tests "dev.skynomads.beerouter.geo.*" --tests "dev.skynomads.beerouter.util.*"`
Expected: PASS

- [ ] **Step 3: Stage changes (DO NOT COMMIT)**

```bash
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/geo/
git add core/src/commonMain/kotlin/dev/skynomads/beerouter/util/
```

---

### Task 10: Update Regression Tests and Final Verification

**Files:**
- Modify: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/map/RoutingMemoryPolicyTest.kt`
- Modify: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/map/CoordinateConversionTest.kt`
- Modify: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/codec/LinkedListContainerTest.kt`
- Modify: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/AcceptedPathTest.kt`
- Modify: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/RoutingTest.kt`
- Modify: `core/src/jvmTest/kotlin/dev/skynomads/beerouter/router/OsmNogoPolygonTest.kt`

- [ ] **Step 1: Update tests that assert on changed exception types**

Check each test file for `assertFailsWith<IllegalArgumentException>` or `assertFailsWith<RuntimeException>` that may now need to match the new exception type. Update as needed:

- `RoutingMemoryPolicyTest.kt:37` — verify it still passes (may use `IllegalArgumentException` intentionally for argument validation)
- `CoordinateConversionTest.kt:234` — same
- `LinkedListContainerTest.kt:35` — same
- `AcceptedPathTest.kt:87` — same (`UnsupportedOperationException` unchanged)
- `RoutingTest.kt:240` — may catch `Throwable` for internal error testing; verify still valid
- `OsmNogoPolygonTest.kt:214` — `@Throws(Exception::class)` on test method; verify still valid

- [ ] **Step 2: Run full core JVM test suite**

Run: `./gradlew :core:jvmTest`
Expected: ALL PASS

- [ ] **Step 3: Stage any test changes (DO NOT COMMIT)**

```bash
git add core/src/jvmTest/
```

---

## Self-Review Checklist

1. **Spec coverage:**
   - ✅ Domain exceptions created (`NoRouteFoundException`, `ProfileParseException`) — Task 1
   - ✅ Throw sites remapped — Tasks 2 & 3
   - ✅ Catch-and-rethrow wrappers removed — Tasks 3 & 4
   - ✅ Silent swallowing fixed — Task 4
   - ✅ `@throws` documentation added to all public API — Tasks 5-9
   - ✅ Regression tests updated — Task 10

2. **Placeholder scan:** No TBD, TODO, or vague instructions.

3. **Type consistency:** `NoRouteFoundException` and `ProfileParseException` used consistently across Tasks 2 and 3. `IllegalStateException` and `IllegalArgumentException` mappings match the spec table.
