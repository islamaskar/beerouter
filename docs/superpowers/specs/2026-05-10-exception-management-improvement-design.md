# Exception Management Improvement — Design Document

**Date:** 2026-05-10
**Scope:** `core` module (`dev.skynomads.beerouter`)
**Approach:** Hybrid (small domain hierarchy + standard exceptions + `@throws` docs)

---

## Background & Motivation

The Beerouter core engine currently uses exception handling inconsistently:

- Most thrown exceptions are untyped (`RuntimeException` with a string message).
- Some standard exceptions are used in the wrong context (e.g., `IllegalArgumentException` for internal state errors, `IllegalStateException` for bad caller input).
- A few locations catch generic `Exception` and rethrow as `RuntimeException`, which destroys the original stack trace semantics and makes debugging harder.
- One location silently swallows all exceptions.
- The public API almost entirely lacks `@throws` KDoc tags, so consumers cannot tell which methods may fail and why.

This design introduces a minimal domain-specific exception hierarchy, fixes misused standard types, removes harmful catch-and-rethrow wrappers, and mandates `@throws` documentation on every public API method.

---

## Goals

1. **Typed exceptions** — callers can distinguish "no route found" from "corrupt data file" without string-parsing.
2. **Correct standard exceptions** — use `IllegalArgumentException` for bad arguments, `IllegalStateException` for broken internal state, `IOException` for I/O, etc.
3. **Remove catch-and-rethrow wrappers** — do not catch generic `Exception` only to wrap it in `RuntimeException`.
4. **Remove silent swallowing** — narrow catch blocks to the specific exception type and document *why* the failure is non-fatal.
5. **Document public API** — every `public` method that throws (or transitively calls something that throws) must have a `@throws` KDoc tag.

## Non-Goals

- Refactoring internal error-handling strategy (e.g., `Result` types, sealed classes) — stay with exceptions.
- Changing the generator module.
- Adding checked exceptions beyond what Kotlin already supports.
- Introducing a large hierarchy of 10+ custom exceptions.

---

## Domain Exception Hierarchy

Two new exceptions are added to the `dev.skynomads.beerouter.router` package. `RoutingIslandException` already exists and is preserved.

```kotlin
/**
 * Thrown when the routing engine cannot find a valid path between waypoints.
 *
 * This replaces the previous `IllegalArgumentException("no track found...")`
 * and similar routing-failure messages so that API consumers can catch
 * routing failures specifically.
 */
public class NoRouteFoundException(message: String) : RuntimeException(message)
```

```kotlin
/**
 * Thrown when a routing profile or lookup file cannot be parsed.
 *
 * This replaces the generic `RuntimeException` and `IllegalArgumentException`
 * wrappers used in `BExpressionContext` during profile parsing.
 */
public class ProfileParseException(message: String, cause: Throwable? = null)
    : RuntimeException(message, cause)
```

`RoutingIslandException` (existing) stays as-is. It already serves its purpose as a control-flow signal inside `RoutingEngine`.

---

## Exception Type Remappings

The following `throw` sites are updated:

| File | Current | Replacement | Rationale |
|------|---------|-------------|-----------|
| `RoutingContext.kt:400` | `RuntimeException("Cannot create path-model...")` | `IllegalArgumentException` | Bad configuration string passed by caller. |
| `TagValueCoder.kt:235` | `RuntimeException("identity corruption!")` | `IllegalStateException` | Internal invariant violated during coding. |
| `BExpressionContext.kt:127` | `RuntimeException("mismatch in variable-count...")` | `IllegalStateException` | Internal encoding mismatch. |
| `BExpressionContext.kt:452` | `RuntimeException("mismatch in variable-count...")` | `IllegalStateException` | Same as above. |
| `BExpressionContext.kt:455` | `RuntimeException(...)` | `IllegalStateException` | Internal variable mismatch. |
| `BExpressionContext.kt:469` | `RuntimeException("no variable for index...")` | `IllegalStateException` | Internal state bug. |
| `MicroCache2.kt:456` | `RuntimeException("ups: self ref?")` | `IllegalStateException` | Internal data-structure corruption. |
| `RoutingEngine.kt:1494` | `IllegalArgumentException("no track found at pass=$cfi")` | `NoRouteFoundException` | Not a caller argument error; routing simply failed. |
| `RoutingEngine.kt:1818` | `IllegalArgumentException("memory limit reached")` | `IllegalStateException` | Routing state exhausted, not a bad argument. |
| `OsmNode.kt:138` | `IllegalArgumentException("unknown cache version...")` | `IllegalStateException` | Unexpected internal cache type. |
| `OsmNode.kt:159` | `IllegalArgumentException("unknown cache version...")` | `IllegalStateException` | Same as above. |
| `OsmNode.kt:322` | `IllegalArgumentException("unlinkLink: unknown source")` | `IllegalStateException` | Internal link state inconsistency. |
| `SearchBoundary.kt:21` | `IllegalArgumentException("undefined direction: $id")` | Keep `IllegalArgumentException` | This **is** a bad argument from the caller. |
| `BExpression.kt:189` | `IllegalArgumentException("unexpected end of file")` | `ProfileParseException` | Parsing failure during profile read. |
| `BExpression.kt:291` | `IllegalArgumentException("unknown expression: $operator")` | `ProfileParseException` | Unknown operator in profile. |

---

## Catch-and-Rethrow Fixes

### `NodesCache.kt:202-206`

**Current:**
```kotlin
catch (re: RuntimeException) { throw re }
catch (e: Exception) { throw RuntimeException("error reading datafile $currentFileName: $e", e) }
```

**Fix:** Remove the generic catch entirely. `RuntimeException` already propagates naturally; there is no value in wrapping checked exceptions as unchecked here. The original stack trace is more useful.

```kotlin
// Removed catch-and-rethrow wrapper; let IOException propagate naturally.
```

### `BExpressionContext.kt:731-735`

**Current:**
```kotlin
catch (e: IllegalArgumentException) {
    throw IllegalArgumentException("ParseException at line $linenr: ${e.message}")
}
catch (e: Exception) {
    throw RuntimeException(e)
}
```

**Fix:** Keep the `IllegalArgumentException` catch because it adds line-number context for parsing errors (convert to `ProfileParseException`). Remove the `catch (e: Exception)` block so that unexpected bugs (e.g., `NullPointerException`) are not masked as generic `RuntimeException`s.

```kotlin
catch (e: IllegalArgumentException) {
    throw ProfileParseException("ParseException at line $linenr: ${e.message}", e)
}
// Removed catch (e: Exception) — do not mask unexpected errors.
```

### `AreaReader.kt:155`

**Current:**
```kotlin
catch (e: Exception) {
    println("AreaReader error after used=$used / count=$count: ${e.message}")
    ais.clear()
}
```

**Fix:** Narrow to `IOException` (the only expected failure mode in this block is reading map data). Document why clearing `ais` is acceptable.

```kotlin
catch (e: IOException) {
    // I/O failure while scanning area info is non-fatal; clear results and continue.
    println("AreaReader I/O error after used=$used / count=$count: ${e.message}")
    ais.clear()
}
```

---

## Silent Swallowing Fix

### `RoutingEngine.kt:409-416`

**Current:**
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

**Fix:** Narrow to `IOException`, remove the misleading comment, and explain *why* the failure is acceptable.

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

---

## `@throws` Documentation Policy

Every `public` method in the core engine that throws (or transitively calls something that throws) must include a `@throws` KDoc tag. The tag names the **most specific** exception type the caller should reasonably expect.

Example:
```kotlin
/**
 * Computes a route through the given waypoints.
 *
 * @param waypoints ordered list of start, via, and destination points
 * @return the computed track, or `null` if only area info was requested
 * @throws NoRouteFoundException if no valid route exists between the waypoints
 * @throws IllegalStateException if the routing context is not fully configured
 * @throws IOException if a required map data file cannot be read
 */
public suspend fun doRouting(waypoints: List<OsmNodeNamed>): OsmTrack?
```

### Public API Surface to Annotate

The following files contain `public` methods that must receive `@throws` tags:

**`router` package**
- `RoutingEngine.kt` — `doRouting`, `doGetInfo`
- `RoutingContext.kt` — `setWaypoint`, `createPath`, `createPrePath`, `freeNoWays`, `restoreNogoList`, `cleanNogoList`, `checkMatchedWaypointAgainstNogos`, `allInOneNogo`, `checkPendingEndpoint`, `distanceBetween`
- `OsmTrack.kt` — `readBinary`, `writeBinary`, `addNodes`, `buildMap`, `prepareSpeedProfile`, `processVoiceHints`
- `OsmNode.kt` — `parseNodeTags`, `parseNodeBody`, `parseNodeBody2`, `addLink`, `unlinkLink`
- `OsmPathElement.kt` — `readFromStream`, `writeToStream`
- `OsmNodeNamed.kt` — `decodeNogo`
- `OsmNogoPolygon.kt` — `intersects`, `isOnPolyline`, `isWithin`, `distanceWithinPolygon`
- `AreaReader.kt` — `getDirectAllData`, `getDirectData`, `writeAreaInfo`, `readAreaInfo`
- `AcceptedPath.kt` — (already has one `@throws`; verify the rest)
- `SearchBoundary.kt` — `create` (or equivalent public entry)
- `VoiceHintList.kt` — `setTransportMode`, `getTransportMode`
- `VoiceHint.kt` — `formatGeometry`, `hasGiveWay`

**`map` package**
- `NodesCache.kt` — `loadSegmentFor`, `getSegmentFor`, `obtainNonHollowNode`, `expandHollowLinkTargets`, `hasHollowLinkTargets`, `getStartNode`, `getGraphNode`, `matchWaypointsToNodes`, `close`, `getElevationType`
- `OsmFile.kt` — `getMicroCache`, `createMicroCache`, `createMicroCacheForCell`, `getDataInputForSubIdx`
- `PhysicalFile.kt` — `checkVersionIntegrity`, `checkFileIntegrity`, `close`
- `OsmNodesMap.kt` — `cleanupAndCount`, `isInMemoryBounds`, `canEscape`, `clearTemp`, `collectOutreachers`, `get`, `put`, `remove`
- `DirectWeaver.kt` — `expandId`
- `WaypointMatcherImpl.kt` — `start`, `transferNode`, `end`, `hasMatch`, `updateWayList`
- `PoiFinder.kt` — `find`, `close`
- `TurnRestriction.kt` — `isTurnForbidden`, `create`
- `GeometryDecoder.kt` — `decodeGeometry`
- `RoutingMemoryPolicy.kt` — `default`, `withTotalHardLimitMegabytes`

**`codec` package**
- `MicroCache.kt` — `emptyCache`, `getAndClear`, `finishNode`, `discardNode`, `collect`, `unGhost`, `getIdForIndex`, `compareWith`, `buildDelta`, `addDelta`, `expandId`, `shrinkId`, `isInternal`, `encodeMicroCache`
- `MicroCache2.kt` — `readUnified`
- `LinkedListContainer.kt` — `addDataElement`, `initList` (already has `@throws`; verify)
- `IntegerFifo3Pass.kt` — `add`
- `TagValueCoder.kt` — `encodeTagValueSet`, `decodeTagValueSet`, `encodeDictionary`
- `StatCoderContext.kt` — `encodeNoisyNumber`, `decodeNoisyNumber`, `encodeNoisyDiff`, `decodeNoisyDiff`, `encodePredictedValue`, `decodePredictedValue`, `encodeSortedArray`, `decodeSortedArray`
- `NoisyDiffCoder.kt` — `encodeSignedValue`, `decodeSignedValue`, `encodeDictionary`

**`expressions` package**
- `BExpressionContext.kt` — `encode`, `decode`, `getKeyValueDescription`, `getMap`, `getLookupKey`, `getLookupValue`, `evaluate`, `createNewLookupData`, `generateRandomValues`, `assertAllVariablesEqual`, `variableName`, `addLookupValue`, `getBooleanLookupValue`, `getOutputVariableIndex`, `setForeignContext`, `getForeignVariableValue`, `getForeignVariableIdx`, `parseProfile`, `setVariableValue`, `getVariableValue`, `getVariableIdx`, `getLookupMatch`, `getLookupNameIdx`, `markLookupIdxUsed`, `isLookupIdxUsed`, `setAllTagsUsed`, `usedTagList`, `getLookupValueIdx`, `parseToken`, `assign`, `checkStartWay`, `freeNoWays`
- `BExpressionContextWay.kt` — all `public` overrides
- `BExpressionContextNode.kt` — all `public` overrides
- `BExpression.kt` — `parse`
- `BExpressionMetaData.kt` — `registerListener`, `readMetaData`
- `BExpressionLookupValue.kt` — `addAlias`, `matches`

**`geo` package**
- `PrimitiveGeoHelper.kt` — any `public` functions that throw
- `Position.kt` — any `public` functions that throw

**`util` package**
- `SortedHeap.kt` — `pop`, `push`
- `MixCoderDataInputStream.kt` — `read`, `readFully`
- `MixCoderDataOutputStream.kt` — `write`, `flush`
- `ByteDataReader.kt` — `readInt`, `readLong`, etc.
- `ByteDataWriter.kt` — `writeInt`, `writeLong`, etc.
- `BitCoderContext.kt` — `encodeVarBits`, `decodeVarBits`, etc.
- `ReducedMedianFilter.kt` — `filter`

---

## Testing Strategy

- **Unit tests** for the new exception types: verify class hierarchy (`NoRouteFoundException` is `RuntimeException`, `ProfileParseException` accepts cause).
- **Regression tests** for remapped throws: update existing tests that use `assertFailsWith<IllegalArgumentException>` to match the new exception type where the exception type was part of the contract (e.g., `RoutingMemoryPolicyTest`, `CoordinateConversionTest`, `LinkedListContainerTest`).
- **No new functional tests** required — this is a refactoring that does not change routing behavior.

---

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| External consumers catch `IllegalArgumentException` for "no route found" | Document in release notes; `NoRouteFoundException` extends `RuntimeException`, not `IllegalArgumentException`, so it is a breaking change for string-based detection. |
| Large diff from `@throws` tags on every public method | Do it as a dedicated, mechanical commit; do not mix with logic changes. |
| Removing `catch (e: Exception)` in `BExpressionContext` exposes previously hidden bugs | Those bugs were already happening; hiding them made debugging harder. Accept the short-term pain for long-term correctness. |

---

## Open Questions

1. Should `RoutingIslandException` be converted to a normal control-flow mechanism (e.g., a sealed result or a dedicated return type) instead of an exception? — **Deferred.** It is used as an internal loop-control signal inside `RoutingEngine`. Changing it is out of scope for this refactor.
2. Should `NoRouteFoundException` extend `IllegalArgumentException` for backward compatibility? — **No.** It is semantically wrong; consumers should update their catch clauses.
