# POI Implementation Summary

This document describes the final implementation of POI support in Beerouter after the plan was
fully carried through in code.

## Scope Delivered

The implementation adds two connected capabilities:

1. `mapcreator` can now preserve configured point-of-interest node tags in generated `.rd5` files.
2. `mapaccess` can now search those POIs inside a bounding box across `.rd5` boundaries.

The implementation reuses the existing rd5 `nodeDescription` payload rather than introducing a new
binary POI section.

## Final Design

### 1. POI tags are encoded through the existing node payload

The implementation keeps the original design choice from the plan:

- POI tags are stored in `NodeData.description`
- those bytes flow through the existing mapcreator pipeline
- they are written into rd5 using the existing node-description field
- they are decoded in `mapaccess` using `BExpressionContextNode`

Relevant files:

- [OsmCutter.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmCutter.kt)
- [NodeData.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/NodeData.kt)
- [OsmNodeP.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmNodeP.kt)
- [OsmNodePT.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmNodePT.kt)
- [OsmNode.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/OsmNode.kt)

This kept the rd5 format compatible with the existing reader/writer path and avoided a new format
fork.

### 2. POI support is lookup-driven

The implementation confirmed the key constraint from the plan: the existing node-description format
can only preserve tags that are present in the lookup metadata.

Because of that, POI support required generated lookup metadata:

- base `misc/profiles2/lookups.dat` is read
- configured POI `key=value` pairs are added to the `---context:node` section if missing
- a generated lookup file is written for the build
- the generated lookup’s minor version is changed so incompatible readers fail fast

Relevant file:

- [GeneratedLookups.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/GeneratedLookups.kt)

### 3. `OsmCutter` became selective only for POI-enabled builds

The original code encoded all node tags that happened to exist in the lookup metadata. That was too
risky once POI tags became configurable.

The final implementation uses:

- full legacy node-tag behavior when POI config is empty
- selective retention when POI config is present

This was important to preserve upstream parity for normal builds.

The retention policy now works like this:

- if no POI config is provided, retain all node tags exactly as before
- if POI config is provided:
  - retain node keys considered routing-relevant from the node lookup/profile set
  - retain the configured POI `key=value` pairs that match the node
  - mark matching POI nodes so they can survive even without any links

Relevant files:

- [NodeTagRetentionPolicy.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/NodeTagRetentionPolicy.kt)
- [OsmCutter.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmCutter.kt)

## Configuration And Generator Wiring

### 4. POI config file support

POI rules are defined as exact `key=value` matches.

Supported format:

```text
# comments allowed
amenity=toilets
amenity=drinking_water
tourism=information
```

Wildcard rules were intentionally not implemented because the current lookup-driven node encoding
does not support arbitrary unseen values safely.

Relevant files:

- [PoiRule.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/PoiRule.kt)
- [PoiGenerationConfig.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/PoiGenerationConfig.kt)
- [PoiConfigParser.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/PoiConfigParser.kt)

### 5. Planet generator and Gradle integration

The whole-planet rd5 generator now accepts an optional POI config path.

Gradle property:

```text
-Pmapcreator.poiConfig=/path/to/pois.conf
```

`PlanetRd5Generator` now:

- parses the POI config
- generates `generated-lookups.dat`
- uses it consistently for `OsmFastCutter` and `WayLinker`
- copies the generated lookup into the final output directory as `lookups.dat`
- copies the POI rules into the output as `poi-rules.conf` when provided

Relevant files:

- [PlanetRd5Generator.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/PlanetRd5Generator.kt)
- [build.gradle.kts](/root/src/mapcreator/build.gradle.kts)

## Standalone POI Retention

### 6. POIs needed to survive without routable links

The biggest implementation issue discovered during testing was that isolated POI nodes were still
getting dropped even after their tags were encoded.

That happened in three places:

1. `NodeFilter` only retained nodes referenced by ways.
2. `WayLinker` ignored nodes with no links when building the segment node lists.
3. `MicroCache2` decoding only marked nodes as valid when they participated in links.

That combination meant POIs like standalone `amenity=toilets` nodes never made it into the final
rd5 output.

The fix was multi-part:

- `NodeData` gained a `retainWithoutLinks` flag
- `OsmCutter` sets that flag for nodes matching a configured POI rule
- `NodeFilter` retains such nodes during the 5x5 pipeline
- `WayLinker` keeps such nodes when building subsegment node lists
- `OsmNodePT` exposes the retention flag through to node serialization
- `MicroCache2` marks nodes with node-tag payloads as valid even if they have zero links

Relevant files:

- [NodeData.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/NodeData.kt)
- [NodeFilter.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/NodeFilter.kt)
- [WayLinker.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/WayLinker.kt)
- [OsmNodeP.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmNodeP.kt)
- [OsmNodePT.kt](/root/src/mapcreator/src/main/kotlin/dev/skynomads/beerouter/mapcreator/OsmNodePT.kt)
- [MicroCache2.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/codec/MicroCache2.kt)

Without those fixes, the POI generator appeared to succeed but the resulting rd5 files contained no
queryable POIs.

## `mapaccess` POI Query API

### 7. New public API

The implementation adds three new public types in `mapaccess`:

- [PoiQuery.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/PoiQuery.kt)
- [PoiResult.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/PoiResult.kt)
- [PoiFinder.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/PoiFinder.kt)

The API uses Beerouter’s internal integer microdegree coordinates directly.

`PoiQuery` supports:

- `minLon`
- `minLat`
- `maxLon`
- `maxLat`
- `requiredTags`
- optional `limit`

`PoiResult` returns:

- `nodeId`
- `position`
- decoded `tags`
- `sourceFile`

### 8. Reader behavior

`PoiFinder` is a reusable object, not a static helper.

It:

- parses lookup metadata once into `BExpressionContextNode`
- caches opened `PhysicalFile` instances
- traverses all rd5 files overlapping the query bbox
- loads overlapping microcaches via `OsmFile`
- decodes node descriptions into tag maps
- filters by required tags
- deduplicates results by Beerouter node id

This follows the intended shape from the plan and mirrors existing traversal patterns from
`NodesCache` and `AreaReader`.

Relevant files:

- [PoiFinder.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/PoiFinder.kt)
- [PhysicalFile.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/PhysicalFile.kt)

## Lookup Compatibility Validation

### 9. Minor-version validation was made strict

The plan called out lookup compatibility as a first-class requirement. The final implementation
enforces that more strictly in the rd5 reader.

`PhysicalFile` now validates:

- major lookup version from index slot 0
- minor lookup version from index slot 1

That matters because POI-enabled builds generate a modified node lookup schema. Using the original
non-generated `lookups.dat` against those rd5 files should not silently decode wrong tags.

Relevant file:

- [PhysicalFile.kt](/root/src/core/src/commonMain/kotlin/dev/skynomads/beerouter/mapaccess/PhysicalFile.kt)

## One Important Deviation From The Original Plan

The original plan suggested also retaining a small display-tag whitelist such as:

- `name`
- `opening_hours`

That was not implemented in this phase.

Reason:

- the current node-description format is lookup-driven and optimized for enumerated lookup values
- arbitrary free-text tags like `name` are not safely representable without extending the lookup
  format or introducing a separate payload mechanism

So the final Phase 1 implementation supports exact configured POI tags only. It does not attempt to
preserve arbitrary text metadata for POIs.

If richer POI payloads are needed later, that would require a follow-up format extension.

## Test Coverage

The final test coverage lives in:

- [MapcreatorTest.kt](/root/src/mapcreator/src/test/kotlin/dev/skynomads/beerouter/mapcreator/MapcreatorTest.kt)

The test suite now covers:

1. Existing rd5 generation still works.
2. Existing upstream parity still holds for normal builds.
3. POI-enabled rd5 generation preserves configured `amenity=toilets` nodes from the Dreieich test
   fixture.
4. `PoiFinder` returns those POIs for a bbox query.
5. `PoiFinder` deduplicates across rd5 boundaries.
6. Lookup mismatch handling fails clearly when querying with incompatible lookup metadata.

### Test fixture details

The implementation reuses the existing Dreieich fixtures:

- [dreieich.pbf](/root/src/mapcreator/src/test/resources/dreieich.pbf)
- [dreieich.osm.gz](/root/src/mapcreator/src/test/resources/dreieich.osm.gz)

The expected POIs are derived directly from the source OSM fixture during the test. This avoids
hard-coding fragile fixture assumptions.

One detail discovered during implementation:

- rd5 does not preserve original OSM node ids
- query results therefore expose Beerouter’s position-derived node id, not the original OSM id

The tests compare against that rd5-stable id.

## Verification

Final verification command:

```bash
./gradlew --no-daemon --project-cache-dir /tmp/beerouter-gradle-cache :mapcreator:test
```

This passed after the final fixes.

## Final Outcome

The delivered implementation provides:

- configurable POI inclusion during rd5 generation
- generated lookup metadata paired with POI-enabled rd5 output
- standalone POI node preservation through the full mapcreator pipeline
- a reusable bbox POI query API in `mapaccess`
- strict lookup compatibility validation
- passing integration coverage for both legacy rd5 generation and the new POI behavior
