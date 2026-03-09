# Changes From Original Plan

The core recommendation is unchanged: reuse the existing rd5 `nodeDescription` payload for POIs and
add a dedicated bbox POI query API in `mapaccess`.

The plan has been tightened in four places based on review:

1. It now makes tag filtering in `OsmCutter` explicit and mandatory to avoid rd5 size inflation.
2. It treats the generated lookup metadata as a first-class artifact that must travel with the rd5
   set, and adds runtime validation requirements around lookup version compatibility.
3. It recommends `PoiFinder` be implemented as a reusable object that caches open rd5 handles,
   instead of a one-shot utility.
4. It clarifies the initial implementation boundary: use integer microdegree bbox coordinates and
   defer any rd5 format changes unless Phase 1 proves insufficient.

# POI In RD5 And Bounding-Box Search Final Plan

## Goal

Add support for points of interest in generated `.rd5` files and expose a `mapaccess` API that can
search POIs inside a bounding box across `.rd5` boundaries.

Example target behavior:

- Configure map generation to preserve nodes tagged `amenity=toilets`.
- Generate `.rd5` files containing those POIs.
- Query `mapaccess` with a bbox plus tag filter `amenity=toilets`.
- Return all matching POIs across tile boundaries without duplicates.

## What The Code Already Supports

### Existing rd5 node payload path

The codebase already has an end-to-end channel for node metadata:

- [OsmCutter.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmCutter.kt)
  encodes node tags into `NodeData.description` using `BExpressionContextNode`.
- [OsmNodeP.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmNodeP.kt)
  writes node description bytes into `MicroCache2` with `writeVarBytes(getNodeDecsription())`.
- [OsmNodePT.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmNodePT.kt)
  is already the “node with extra payload” representation in map creation.
- [OsmNode.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/OsmNode.kt)
  decodes the payload back into `nodeDescription`.

This is the right mechanism to reuse for POIs.

### Existing rd5 scan primitives

`mapaccess` already has the primitives needed for bbox search:

- [PhysicalFile.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/PhysicalFile.kt)
  opens an rd5 file and exposes the 5x5 tile index.
- [OsmFile.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/OsmFile.kt)
  loads the subcell index and individual microcaches.
- [MicroCache2.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache2.kt)
  already decodes node descriptions and link data.
- [NodesCache.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/NodesCache.kt)
  contains the canonical logic for mapping lon/lat degrees to rd5 file names.
- [AreaReader.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt)
  already traverses overlapping cells across multiple rd5 files.

### Existing lookup decoding

- [BExpressionContextNode.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContextNode.kt)
  and [BExpressionContext.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/expressions/BExpressionContext.kt)
  already decode encoded node-description bytes back into a `Map<String, String>`.

That means `mapaccess` does not need a new tag decoder.

## Main Constraint

The current node payload format is lookup-driven.

The encoded node-description bytes only preserve tags that exist in the lookup metadata used during
generation. For example, current [lookups.dat](/root/src/misc/profiles2/lookups.dat) does not
contain `amenity=toilets` in the node context, so that tag will not survive unless lookup metadata
is extended.

That creates one hard requirement:

- The rd5 writer and the POI reader must use compatible lookup metadata.

## Recommendation

Implement POI support in two layers:

1. Reuse the existing `nodeDescription` payload for POI tags.
2. Add a dedicated bbox POI query API in `mapaccess`.

Do not add a new rd5 binary payload in the first phase.

This is the lowest-risk path because:

- it reuses the current writer and reader format,
- it avoids rd5 format divergence,
- it keeps routing unaffected,
- it minimizes implementation surface.

## Phase 1 Architecture

### 1. POI generation configuration

Add a config model in `mapcreator`, for example:

- `PoiRule(key: String, values: Set<String>?)`
- `PoiGenerationConfig(rules: List<PoiRule>, retainedTags: Set<String>)`

Recommended semantics:

- exact key/value match: `amenity=toilets`
- optional key-only wildcard: `amenity=*`

Recommended config file format:

```text
# comments allowed
amenity=toilets
amenity=drinking_water
tourism=information
```

Implementation pieces:

- `PoiRule.kt`
- `PoiGenerationConfig.kt`
- `PoiConfigParser.kt`

The parser should:

- allow blank lines and comments,
- normalize whitespace,
- reject malformed entries early.

### 2. Generated lookup metadata

Create a generated lookup file for POI-enabled builds.

Why:

- arbitrary POI tags must exist in `---context:node`,
- the base `misc/profiles2/lookups.dat` should not be manually mutated per use case.

Implementation:

- read the base [lookups.dat](/root/src/misc/profiles2/lookups.dat),
- preserve file structure and section ordering,
- append configured POI values only if missing,
- write a generated file under the mapcreation work directory, e.g.
  `generated-lookups.dat`.

Rules:

- only modify the `---context:node` section,
- keep the file syntactically identical to what `BExpressionMetaData` expects,
- increment minor lookup version when the generated POI schema changes.

This generated lookup file becomes a first-class artifact of rd5 generation.

### 3. Mandatory tag filtering in `OsmCutter`

This is the most important adjustment from the original plan.

Problem:

- [OsmCutter.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmCutter.kt)
  currently encodes all node tags that match lookup metadata.
- If lookup metadata is widened too much, rd5 size will grow substantially.

Required change:

- `OsmCutter.nextNode` must become selective.

Encoding policy for node payloads should be:

1. routing-required node tags, if any,
2. explicitly retained POI tags from `PoiGenerationConfig`,
3. a very small allowed set of display tags for matched POIs.

Recommended first-pass retained display tags:

- `name`
- `opening_hours`

Possible later additions:

- `brand`
- `operator`
- `website`

Behavior:

- If a node does not match a configured POI rule and has no routing-relevant node tags, do not
  store a node description.
- If a node matches a configured POI rule, only store the whitelisted subset of tags.

This keeps rd5 growth bounded.

### 4. Wire config through generation tasks

Extend:

- [mapcreator/build.gradle.kts](/root/src/mapcreator/build.gradle.kts)
- [PlanetRd5Generator.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/PlanetRd5Generator.kt)

Suggested inputs:

- `-Pmapcreator.poiConfig=/path/to/pois.conf`
- generated lookup file emitted into the working directory

The generation pipeline should then use the generated lookup file consistently in:

- `OsmFastCutter`
- `WayLinker`
- any later parity or verification tests

### 5. Add a dedicated `mapaccess` POI API

Add new public types:

- `PoiQuery`
- `PoiResult`
- `PoiFinder`

Suggested shape:

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
```

Important:

- use Beerouter’s integer microdegree coordinates in the public API,
- do not use floating-point bbox coordinates in the first version,
- include raw `nodeId` in results.

### 6. Make `PoiFinder` long-lived

`PoiFinder` should not be a one-shot static helper.

It should be a reusable object that:

- holds initialized `BExpressionContextNode`,
- caches `PhysicalFile` handles or equivalent file access state,
- reuses buffers where practical,
- supports repeated bbox queries cheaply.

This should follow the operational pattern of [NodesCache.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/NodesCache.kt),
but without pulling in routing graph concerns.

### 7. Implement bbox scanning using existing cell traversal

Use the same tiling logic already present in:

- [NodesCache.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/NodesCache.kt)
- [AreaReader.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/router/AreaReader.kt)

Traversal algorithm:

1. Compute all intersecting 1x1 degree logical segments or directly all overlapping rd5 subcells.
2. Group them by rd5 file name.
3. Open each rd5 file once.
4. For each overlapping subcell:
   - load `MicroCache2`,
   - iterate nodes,
   - skip nodes with `nodeDescription == null`,
   - decode tags with `BExpressionContextNode`,
   - check bbox containment,
   - check tag filter,
   - emit result if not already seen.

### 8. Deduplicate across rd5 boundaries

This is required.

Border nodes are duplicated by generation, so POI queries must dedupe results by `nodeId`.

Implementation:

- maintain a `MutableSet<Long>` of seen node ids,
- only emit the first result for a given `nodeId`.

Additional refinement:

- use half-open bbox semantics internally where convenient,
- but rely on `nodeId` dedupe for correctness.

## Runtime Validation Requirements

The generated lookup file should ship with the generated rd5 dataset.

The POI reader must validate compatibility in one of these ways:

1. Directly compare expected lookup version and minor version with rd5 header metadata.
2. Require the caller to provide the exact generated lookup content and fail early if versions do
   not match.

Recommended behavior:

- `PoiFinder` should require lookup content explicitly.
- On first file access, validate `lookupVersion` and `lookupMinorVersion`.
- If incompatible, throw a clear error telling the caller that the POI lookup metadata does not
  match the rd5 set.

## Optional Phase 1.5 Optimization

Do not build this initially.

If bbox scanning proves CPU-heavy, consider a future optimization:

- mark in the subcell or microcache header whether that cell contains any node-description payload
  at all, or any POI-tagged nodes specifically.

That would allow fast skipping of empty cells, but it is an optimization, not part of the first
delivery.

## Deferred Alternative: Dedicated POI Payload

Only consider this if the lookup-driven reuse path becomes limiting.

That would require:

- new rd5 writer logic,
- new `MicroCache2` binary structure,
- new reader logic,
- long-term divergence from upstream BRouter.

Do not start there.

## Detailed Implementation Steps

### Step 1: POI config parser and model

Add:

- `PoiRule.kt`
- `PoiGenerationConfig.kt`
- `PoiConfigParser.kt`

Requirements:

- comments supported,
- invalid lines rejected,
- wildcard semantics defined clearly.

### Step 2: Generated lookup merger

Add a small utility that:

- reads base lookup content,
- injects configured POI key/value entries into `---context:node`,
- updates minor version,
- writes `generated-lookups.dat`.

This file must be treated as part of the produced dataset.

### Step 3: Selective node tag encoding

Refactor [OsmCutter.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmCutter.kt)
so node payload encoding is driven by a retained-tag filter.

Add a helper concept such as:

- `RetainedNodeTagPolicy`

Responsibilities:

- determine if a node matches a POI rule,
- derive the subset of tags to encode,
- avoid storing broad nonessential tags globally.

### Step 4: Generator integration

Extend generation tasks and CLI entry points to accept:

- POI config path
- output path for generated lookup metadata

Ensure generated lookup content is used everywhere in the pipeline.

### Step 5: `PoiFinder` implementation

Add new files in `core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/`:

- `PoiFinder.kt`
- `PoiQuery.kt`
- `PoiResult.kt`

Implementation approach:

- internal file caching patterned after `NodesCache`,
- bbox-to-cell traversal patterned after `AreaReader`,
- node decode patterned after `OsmNode.parseNodeBody`.

### Step 6: Tag decode and filter

For each node with `nodeDescription`:

- decode via `BExpressionContextNode.getMap(false, nodeDescription)`,
- apply the requested tag filter,
- create `PoiResult`.

### Step 7: Boundary dedupe

Add `seenNodeIds: MutableSet<Long>` to query execution.

This is not optional.

### Step 8: Documentation

Document:

- how to enable POI generation,
- where generated lookup metadata is written,
- requirement that POI queries use matching lookup content,
- expected impact of broad POI tag retention on rd5 size.

## Testing Plan

### Mapcreator tests

1. Generate rd5 with POI config `amenity=toilets`.
2. Verify a known toilet node retains encoded node description.
3. Verify generation without that POI config omits the tag.
4. Verify generated lookup file includes the required node-context entries.

### Mapaccess tests

1. Single-rd5 bbox query returns one known POI.
2. Cross-rd5 boundary query returns exactly one logical result for a duplicated border node.
3. Cross-subcell boundary query works within one rd5 file.
4. Lookup mismatch fails clearly.
5. `limit` behavior works if implemented in first pass.

### Regression tests

1. Existing routing tests continue to pass with POI-disabled generation.
2. A POI-enabled generation run still produces routing-compatible rd5 files.
3. The addition of POI retention does not alter route costs or graph topology.

## Risks And Mitigations

### Risk: lookup metadata drift

Mitigation:

- treat generated lookup content as a shipped artifact,
- validate lookup version compatibility at read time,
- document the coupling explicitly.

### Risk: rd5 size inflation

Mitigation:

- make selective node-tag encoding mandatory,
- only retain configured POI tags plus a small whitelist,
- reject overly broad defaults.

### Risk: duplicate POIs at boundaries

Mitigation:

- dedupe by `nodeId`,
- add dedicated boundary tests.

### Risk: bbox scan CPU cost

Mitigation:

- make `PoiFinder` reusable and caching,
- defer binary-format optimizations unless profiling proves they are needed.

## Delivery Order

1. POI config parser and generated lookup merger.
2. Selective node-tag retention in `OsmCutter`.
3. Generator wiring in Gradle and `PlanetRd5Generator`.
4. `PoiFinder` with bbox scan and dedupe.
5. Compatibility validation against generated lookup metadata.
6. End-to-end tests and docs.

## Final Recommendation

Proceed with Phase 1.

The right implementation is:

- reuse `nodeDescription`,
- generate lookup metadata that includes configured POI node tags,
- make `OsmCutter` selectively encode only retained node tags,
- add a reusable `PoiFinder` in `mapaccess` that scans overlapping rd5 subcells and deduplicates by
  node id.

This meets the requirement with minimal rd5 format risk and maximum reuse of the current
Beerouter/BRouter architecture.
