# POI In RD5 And Bounding-Box Search Plan

## Goal

Add support for points of interest in generated `.rd5` files and expose a `mapaccess`
feature that searches POIs inside a bounding box, including results that span multiple
`.rd5` files.

Example target behavior:

- Configure map generation to preserve nodes tagged `amenity=toilets`.
- Generate `.rd5` files containing those POIs.
- Query `mapaccess` with a bounding box and filter such as `amenity=toilets`.
- Return all matching POIs across tile boundaries without duplicates.

## Current State

### What already exists

- `mapcreator` already preserves node-tag payloads through the rd5 pipeline:
  - [mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmCutter.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmCutter.kt)
    encodes node tags into `NodeData.description` using `BExpressionContextNode`.
  - [mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmNodeP.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmNodeP.kt)
    writes node description bytes into the microcache with `writeVarBytes(getNodeDecsription())`.
  - [core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/OsmNode.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/OsmNode.kt)
    decodes that payload into `nodeDescription`.

- `mapaccess` already knows how to:
  - open `.rd5` files via [PhysicalFile.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/PhysicalFile.kt),
  - access 1/divisor subcells via [OsmFile.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/OsmFile.kt),
  - decode microcaches via [MicroCache2.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache2.kt),
  - traverse neighboring cells and `.rd5` files via [NodesCache.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/NodesCache.kt) and [AreaReader.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt).

### What does not exist

- No dedicated POI query API in `mapaccess`.
- No generator configuration for preserving arbitrary POI tags.
- No bbox scanner that returns nodes based on decoded node tags.
- No explicit deduplication strategy for cross-`.rd5` boundary POI queries.

## Key Constraint

The existing node-description bytes are lookup-driven.

- [core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt)
  encodes and decodes values based on lookup metadata.
- [misc/profiles2/lookups.dat](/root/src/misc/profiles2/lookups.dat) currently does not include
  `amenity=toilets` in the node context.

This means:

- We can reuse the existing rd5 node payload path.
- But arbitrary POI tags only survive if they exist in the node lookup metadata used at generation time.

That drives the design choice:

- Preferred first implementation: reuse `nodeDescription` and generate a lookup file extended with configured POI tags.
- Avoid introducing a second custom POI binary payload in rd5 unless the lookup-based approach proves too restrictive.

## Recommended Architecture

### Phase 1: Reuse existing node-description bytes

This is the lowest-risk implementation because it aligns with the existing rd5 write/read format.

#### Map creation side

1. Add POI generation configuration.
   - Introduce a config model in `mapcreator`, for example:
     - `PoiRule(key: String, values: Set<String>?)`
     - `PoiGenerationConfig(rules: List<PoiRule>, retainedTags: Set<String>)`
   - Supported matching:
     - exact key/value, e.g. `amenity=toilets`
     - optionally key-only, e.g. `amenity=*`

2. Extend lookup metadata at generation time.
   - Build a merged lookup file derived from:
     - base `misc/profiles2/lookups.dat`
     - configured POI keys/values for the node context
   - Feed that merged lookup file into:
     - `OsmCutter`
     - all later generation stages
     - any tests that decode POI tags
   - Bump minor lookup version when POI config changes, so mismatched lookup content fails clearly.

3. Preserve matching node tags in `OsmCutter`.
   - In [OsmCutter.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmCutter.kt),
     add POI rule matching while raw node tags are still available.
   - If a node matches any configured POI rule:
     - ensure its node lookup payload contains the configured POI tags,
     - optionally also preserve `name`, `brand`, `operator`, `opening_hours`, `website`.
   - Recommended first-pass retained tags:
     - required match tags
     - `name`
     - `opening_hours`
   - Keep this separate from routing logic. POI retention should not alter access or cost behavior.

4. Expose POI configuration from Gradle.
   - Extend [mapcreator/build.gradle.kts](/root/src/mapcreator/build.gradle.kts) and
     [PlanetRd5Generator.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/PlanetRd5Generator.kt)
     to accept POI config input.
   - Prefer a config file over many ad-hoc properties.
   - Example:
     - `-Pmapcreator.poiConfig=/path/to/pois.conf`
   - Example config syntax:
     - `amenity=toilets`
     - `amenity=drinking_water`
     - `tourism=information`

#### Map access side

5. Add a POI query API in `mapaccess`.
   - New public types, for example:
     - `PoiQuery`
     - `PoiResult`
     - `PoiFinder`
   - `PoiQuery` should include:
     - bounding box
     - tag filter predicate or `requiredTags`
     - optional limit
     - optional inclusion of decoded tags
   - `PoiResult` should include:
     - `nodeId`
     - `position`
     - `tags`
     - `name`
     - `sourceFile`

6. Implement bbox scanning by reusing existing tile traversal logic.
   - Reuse filename derivation from [NodesCache.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/NodesCache.kt).
   - Reuse subcell loading from [OsmFile.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/OsmFile.kt).
   - Reuse node decoding from [OsmNode.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/OsmNode.kt).
   - Use a dedicated traversal path instead of overloading routing APIs.

7. Decode node tags with `BExpressionContextNode`.
   - The POI finder should receive either:
     - lookup content string, or
     - an already initialized `BExpressionContextNode`.
   - For each decoded node with `nodeDescription != null`:
     - decode tags via `BExpressionContextNode.getMap(false, nodeDescription)`,
     - test against the POI filter,
     - test position against the bbox,
     - emit `PoiResult`.

8. Deduplicate across `.rd5` boundaries.
   - Border nodes are duplicated in generation.
   - During bbox scan, deduplicate by `node.idFromPos`.
   - Use half-open bbox rules internally where practical:
     - include west/south edges
     - exclude east/north edges
   - Still keep `id`-based dedupe as the real correctness guard.

## Alternative Architecture

### Phase 2 option: Dedicated POI payload in rd5

Only do this if Phase 1 is not sufficient.

This would mean:

- extending the rd5 node encoding with a new feature block or appended POI payload,
- extending `MicroCache2` encoding and decoding,
- making that payload independent of lookup metadata,
- probably introducing a separate compact POI schema.

This would support arbitrary POI keys without modifying `lookups.dat`, but it is materially riskier:

- binary format changes,
- parity with upstream BRouter intentionally diverges,
- more reader/writer maintenance burden,
- higher regression risk in routing data decoding.

Recommendation: do not start here.

## Proposed API Shape

### Generator

- `PlanetRd5Generator` accepts a POI config file.
- Add a parser in `mapcreator`, for example:
  - `PoiConfigParser.parse(file: File): PoiGenerationConfig`

Example file:

```text
amenity=toilets
amenity=drinking_water
tourism=information
```

### Mapaccess

Suggested new API surface:

```kotlin
data class PoiQuery(
    val minLon: Int,
    val minLat: Int,
    val maxLon: Int,
    val maxLat: Int,
    val requiredTags: Map<String, Set<String>> = emptyMap(),
    val limit: Int? = null
)

data class PoiResult(
    val nodeId: Long,
    val position: Position,
    val tags: Map<String, String>,
    val sourceFile: String?
)

class PoiFinder(
    private val mapAccessFileSystem: MapAccessFileSystem,
    lookupContent: String
) {
    fun findPois(query: PoiQuery): List<PoiResult>
}
```

Notes:

- Keep this independent of `RoutingContext` initially.
- If useful later, `RoutingContext.poipoints` can be populated from `PoiFinder`.

## Detailed Implementation Steps

### Step 1: POI config model and parser

- Add:
  - `PoiRule.kt`
  - `PoiGenerationConfig.kt`
  - `PoiConfigParser.kt`
- Decide config location:
  - file path supplied by Gradle property is preferred.

### Step 2: Generated lookup file

- Add a utility in `mapcreator` to extend node-context lookup metadata.
- Preserve the original file ordering as much as possible.
- Append POI values only if missing.
- Write merged output under the work directory, for example:
  - `work/generated-lookups.dat`

### Step 3: Thread POI config through generation

- Extend:
  - [PlanetRd5Generator.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/PlanetRd5Generator.kt)
  - [mapcreator/build.gradle.kts](/root/src/mapcreator/build.gradle.kts)
- Pass generated lookup file to:
  - `OsmFastCutter`
  - `WayLinker`
  - any later decoding steps that depend on lookup version

### Step 4: Match POIs in `OsmCutter`

- In [OsmCutter.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmCutter.kt):
  - inspect raw node tags,
  - determine whether the node matches configured POI rules,
  - encode only the desired subset of tags into `NodeData.description`.
- Be careful not to inflate rd5 excessively by storing all node tags for all nodes.

### Step 5: Add bbox traversal in `mapaccess`

- Add:
  - `PoiFinder.kt`
  - `PoiQuery.kt`
  - `PoiResult.kt`
- Reuse tile computation logic from:
  - [NodesCache.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/NodesCache.kt)
  - [AreaReader.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt)

### Step 6: Decode and filter results

- For each intersecting subcell:
  - decode microcache,
  - iterate nodes,
  - skip nodes without `nodeDescription`,
  - decode `nodeDescription` with `BExpressionContextNode`,
  - apply bbox test and tag filter,
  - dedupe on `nodeId`,
  - add `PoiResult`.

### Step 7: Boundary correctness

- Explicitly test:
  - POI entirely inside one rd5,
  - POI near a subcell boundary,
  - POI on a 5x5 rd5 border,
  - bbox overlapping two rd5 files,
  - duplicate suppression by node id.

### Step 8: Optional integration with routing

- Later, if wanted:
  - populate `RoutingContext.poipoints` from `PoiFinder`,
  - expose higher-level convenience methods near `RoutingEngine` or `RoutingContext`.
- This should be deferred until the base `mapaccess` feature is stable.

## Testing Plan

### Mapcreator tests

1. Add a test fixture with a known POI such as `amenity=toilets`.
2. Generate rd5 with a POI config that includes that tag.
3. Decode the resulting node payload and assert the POI tag survived.
4. Assert generation without the POI config excludes that tag.

### Mapaccess tests

1. Single-file bbox test:
   - query a bbox containing one known POI,
   - expect exactly one result with matching tags.

2. Cross-rd5 boundary test:
   - place or choose a POI near a 5x5 file boundary,
   - query a bbox crossing both files,
   - expect one logical result.

3. Cross-subcell boundary test:
   - same concept within one rd5 file across microcache boundaries.

4. Lookup mismatch test:
   - attempt POI decoding with incompatible lookup metadata,
   - assert failure is explicit and understandable.

5. Limit/filter test:
   - multiple POIs in bbox,
   - filter by exact tag,
   - verify `limit` behavior if implemented.

## Risks

### 1. Lookup metadata drift

If the generator uses a POI-extended lookup file but runtime decoding uses the base
lookup file, node descriptions will decode incorrectly or fail version checks.

Mitigation:

- make the generated lookup file a first-class artifact of POI-enabled generation,
- document that POI queries must use matching lookup content.

### 2. RD5 size growth

Preserving too many node tags on too many nodes will enlarge rd5 files noticeably.

Mitigation:

- only store configured POI tags plus a very small allowed set such as `name`,
- avoid storing arbitrary node tags by default.

### 3. Duplicate results at boundaries

Mapcreator duplicates border nodes by design.

Mitigation:

- dedupe by `nodeId`,
- add dedicated boundary tests.

### 4. Binary format divergence

If a new dedicated POI payload is added too early, the rd5 format diverges further from
upstream BRouter and increases maintenance cost.

Mitigation:

- use the existing node-description channel first.

## Recommended Delivery Order

1. POI config model and generated lookup file.
2. `OsmCutter` support for configured POI tag retention.
3. `mapaccess` bbox POI finder with node-description decoding.
4. Cross-boundary deduplication.
5. End-to-end tests.
6. Optional routing integration.

## Recommendation

Implement POIs in rd5 by reusing the existing node-description payload and extending the
node lookup metadata with configured POI tags at generation time.

Then add a dedicated `mapaccess` bbox scanning API that:

- traverses all overlapping subcells and `.rd5` files,
- decodes node descriptions with `BExpressionContextNode`,
- filters by requested POI tags,
- deduplicates by node id.

This gives the requested functionality with the least binary-format risk and the highest
reuse of the current Beerouter/BRouter architecture.
