# Review of POI In RD5 And Bounding-Box Search Plan

## Executive Summary

The plan to add POI support by reusing the existing `nodeDescription` payload in `.rd5` files is *
*sound and highly recommended**. It leverages the existing Huffman-compressed storage mechanism and
avoids breaking changes to the binary format.

The strategy of extending `lookups.dat` at generation time is a clever way to support arbitrary tags
without modifying the core library's master metadata, provided that the same extended metadata is
available at query time.

## Architectural Assessment

### 1. Reuse of `nodeDescription` (Phase 1)

- **Strength**: This is the most surgical approach. `nodeDescription` is already designed to carry
  arbitrary key-value pairs encoded via `BExpressionContextNode`.
- **Strength**: Leveraging `MicroCache2` ensures that POIs benefit from the existing spatial
  indexing (1/divisor grid) and statistical compression.
- **Strength**: Minimal impact on the existing routing engine.

### 2. Lookup Metadata Extension

- **Strength**: Allows flexibility for different use cases (e.g., a "toilets" map vs. a "drinking
  water" map) without hardcoding tags in the core library.
- **Risk**: As noted in the plan, "Lookup metadata drift" is the primary failure mode. If the reader
  and writer don't use the exact same `lookups.dat`, the decoded tags will be incorrect.
- **Recommendation**: The generated `.rd5` files should ideally be packaged with the
  `generated-lookups.dat` used to create them, or the `PoiFinder` should verify the `lookupVersion`
  and `lookupMinorVersion` stored in the `.rd5` header.

### 3. Map Access & Search

- **Strength**: The proposed `PoiFinder` API is clean and follows the patterns in `NodesCache`.
- **Efficiency**: Scanning subcells by iterating `OsmFile` tiles is efficient for localized bbox
  queries.
- **Deduplication**: Using `nodeId` (derived from position) is the correct way to handle the
  unavoidable duplication of border nodes in the BRouter format.

## Critical Risks & Mitigations

### 1. RD5 Size Inflation (High Priority)

The current `OsmCutter` implementation encodes **all** tags that happen to match the lookup
metadata. If the POI-extended lookup includes broad keys (like `amenity=*` or common tags like
`name`), the size of every node in the `.rd5` will grow significantly.

- **Mitigation**: Modify `OsmCutter.nextNode` to be selective. It should only encode tags that are
  either:
    1. Required for routing (e.g., `highway`, `access`).
    2. Explicitly configured as "POI tags" in the `PoiGenerationConfig`.
- **Implementation Detail**: `OsmCutter` should pass a filter to `BExpressionContextNode` during
  encoding to ensure only relevant tags are stored.

### 2. Performance of BBox Scanning

While efficient, decoding many `MicroCache2` blocks can be CPU-intensive due to the BitCoder/Huffman
decoding.

- **Mitigation**: If performance becomes an issue, consider a "Phase 1.5" where a bit is set in the
  `MicroCache2` header to indicate if the cell contains *any* POIs at all. However, this is likely
  unnecessary for initial delivery.

### 3. Coordinate Precision

Beerouter uses `Int` for micro-degrees. Ensure the `PoiQuery` uses the same precision to avoid
rounding errors at tile boundaries.

## Detailed Plan Feedback

### Mapcreator Side

- The `PoiConfigParser` should support comments and perhaps grouping of tags.
- The utility to merge `lookups.dat` should ensure it doesn't break the `---context:node` section
  structure.

### Mapaccess Side

- `PoiFinder` should probably be a long-lived object that caches `PhysicalFile` handles to avoid
  repeated I/O overhead.
- Ensure `PoiResult` includes the raw `nodeId` to allow clients to cross-reference with other data
  sources or routing results.

## Final Recommendation

**Proceed with Phase 1.** The plan is technically solid and aligns with Beerouter's design
philosophy.

**Priority implementation detail**: Ensure `OsmCutter` does not blindly encode every tag that
matches the lookup. Adding a "retained tags" filter during the `OsmCutter` encoding pass is
essential to keep `.rd5` files at a manageable size.

