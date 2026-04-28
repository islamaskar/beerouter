# StdPath Full Extraction Design

## Context

The accepted-path safety phase introduced `RoutingEvaluationState`, `PathTraceBuilder`, `StdPathState`, `AcceptedPath`, and `StdPathCandidate`, then isolated the non-detail `StdModel` routing branch behind `RoutingContext.evaluateStdCompatibilityCandidate`. That phase still evaluates rejected candidates by allocating compatibility `StdPath` instances through `RoutingContext.createPath()`.

The next phase replaces that compatibility allocation path for non-detail `StdModel` routing. It should persist accepted search records as `AcceptedPath` and evaluate rejected transitions in reusable `StdPathCandidate` scratch.

## Goals

- Stop allocating `StdPath` objects for rejected non-detail `StdModel` candidates.
- Store non-detail `StdModel` search survivors as `AcceptedPath` records in link holders and the open set.
- Preserve route behavior for the committed route guard and focused routing tests.
- Keep kinematic routing, guide-track detours, direct/same-segment setup, and detail-mode transfer-node output on existing compatibility `OsmPath` code.
- Keep the migration incremental enough to commit and verify each boundary separately.

## Non-Goals

- Do not migrate `KinematicPath` or `KinematicPrePath`.
- Do not remove search-time `OsmPathElement` creation from detail-mode routing.
- Do not redesign cost-cutting, guide-track detour generation, or direct routing.
- Do not pool accepted paths.

## Approved Architecture

### AcceptedPath Bridge

`AcceptedPath` becomes the persistent `StdModel` non-detail search record. During this phase it should extend the existing `OsmPath` bridge so `RoutingEngine`, `SortedHeap`, and `OsmLinkHolder` can keep their current typed structure while storing accepted records. The bridge owns accepted common state: parent, source and target nodes, link, cost, air distance, tree depth, origin position, elevation, path flags/classifier fields, and `StdPathState`.

`AcceptedPath` does not evaluate transitions. Its path-evaluation methods should fail fast if accidentally used. It can keep mutable `airdistance` for existing invalidation semantics.

### StdPathCandidate Evaluation

`StdPathCandidate` owns reusable mutable state for one origin/link transition. It copies accepted state from `AcceptedPath`, evaluates the non-detail subset of current `OsmPath.addAddionalPenalty()`, and returns rejection without allocation. Candidate evaluation must run inside the existing `RoutingContext` transaction so rejected candidates do not leak `nogoCost`, `shortestmatch`, endpoint, or waypoint state.

The first extraction can duplicate the non-detail logic from `OsmPath.addAddionalPenalty()` rather than attempting to share a large generic evaluator. This keeps kinematic and detail behavior untouched.

### Dominance And Snapshot

The non-detail `StdModel` branch compares candidate scratch against existing `AcceptedPath` dominators using the same elevation-correction semantics as `StdPath.definitlyWorseThan()`. A candidate only snapshots into `AcceptedPath` after it survives local best selection, radius/cutoff checks, and dominance checks.

### Trace Conversion

`PathTraceBuilder` gains conversion from `AcceptedPath` parent chains to `OsmPathElement` chains for final tracks and cost-cutting match paths. Detail detour conversion remains compatibility-path based in this phase.

### Routing Loop Scope

Only the branch guarded by `currentGuideTrack == null && routingContext.isStdModel()` switches to accepted/candidate storage. Other branches continue to call `RoutingContext.createPath()` and recycle compatibility paths.

## Tests And Verification

- Unit tests prove `AcceptedPath` exposes bridge state and fails fast for transition evaluation.
- Unit tests compare `StdPathCandidate.evaluate()` against compatibility `StdPath` on the same simple link.
- Unit tests prove rejected candidate evaluation restores `RoutingContext` state.
- Unit tests prove `PathTraceBuilder` can convert an `AcceptedPath` parent chain.
- Focused route guard `RoutingTest.generatedTrekkingAdjacentRouteRemainsStable` must stay at distance `15` and node ids `[811979274142721743, 811978599832856189]`.
- Route 9 / gravel / `pass1coefficient=1.5` must still print `distance_m=350191` in `:benchmark:singleRoutePerformance`.
- Broad `:core:jvmTest` currently has known post-codec parity failures; use focused tests plus baseline comparison if new broad failures appear.

## Risks

- `OsmPath` hides important path flags and classifier fields behind protected members. The bridge needs explicit copy/export helpers or protected access through subclassing.
- `OsmPath.addAddionalPenalty()` includes detail-mode transfer-node behavior. The extracted candidate evaluator must only replace non-detail Std routing.
- Cost-cutting currently reads parent cost through `originElement`; accepted-path trace conversion must provide an equivalent parent-chain element before match-path merging.
- The route result is sensitive to integer truncation and float rounding. Candidate extraction should preserve statement order and numeric casts from `OsmPath`/`StdPath`.
