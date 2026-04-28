# StdPath Redesign Design

## Context

The current routing hot path uses `OsmPath`/`StdPath` as a single object for three different lifetimes:

- Candidate evaluation while testing `origin + link` combinations.
- Accepted search state stored in link holders and `openSet`.
- Traceback/detail state later converted to `OsmPathElement` chains for tracks, detours, voice hints, and cost-cutting merges.

This shape keeps the code close to BRouter, but it makes ownership unclear. A path that is only a failed candidate has the same allocation and reset burden as a path that escapes into link holders, the heap, cost-cutting state, or final track output. Previous safe recycling reduced some discarded-candidate allocations, but `StdModel.createPath()` remains the largest allocation site. Broader pooling is risky because accepted paths can still be referenced by the heap, link holders, parent/traceback chains, guide detours, and cost-cutting tracks.

The long-term redesign should separate candidate scratch state from persistent accepted path history and from exported `OsmPathElement` track nodes.

## Goals

- Make routing path ownership explicit.
- Stop allocating persistent `StdPath` objects for rejected candidate evaluations.
- Keep accepted path records stable while they can be referenced by link holders, `openSet`, cost-cutting, or final traceback.
- Move `OsmPathElement` creation out of the normal search loop except for detail-mode outputs that genuinely need it.
- Preserve existing routing behavior, including cost-cutting, guide-track detours, voice hints, kinematic paths, and direct routing.
- Migrate incrementally so each phase has focused tests and measurable profiling output.

## Non-Goals

- Do not redesign the routing algorithm itself.
- Do not introduce general object pooling for accepted paths.
- Do not replace `Position` with primitive coordinate fields as the public path/track representation.
- Do not change profile semantics, route results, or guide-track output format.
- Do not optimize `DirectWeaver` graph materialization in this redesign.

## Proposed Architecture

### AcceptedPath

`AcceptedPath` is the persistent search record. It may be stored in `openSet`, link holders, cost-cutting state, and final traceback roots.

It owns the state that must survive after a candidate is accepted:

- `parent: AcceptedPath?`
- `sourceNode`, `targetNode`, and `link`
- `cost`, `airdistance`, and `treedepth`
- path flags currently stored in `OsmPath.bitfield`
- classifier state: `lastClassifier`, `lastInitialCost`, `priorityclassifier`
- `originPosition` and `selev`
- model-specific hot state, initially represented by a model-state object
- optional detail payload for message and transfer-node output
- `nextForLink` so existing `OsmLinkHolder` integration can be retained during migration

Accepted paths are not recycled while a route search is active. They become eligible for collection when the routing search and any derived tracks are done.

### PathCandidate

`PathCandidate` is reusable scratch for evaluating one possible transition from an accepted origin over one link.

It holds mutable copies of the same hot fields needed by `addAdditionalPenalty` and `processWaySection`, but it never escapes into:

- `openSet`
- `OsmLink` link-holder chains
- `OsmTrack`
- cost-cutting `matchPath`
- guide detour maps

Candidate evaluation produces either:

- rejection with no allocation, or
- a snapshot into a new `AcceptedPath` after radius, cutoff, and dominance checks pass.

The routing loop should keep two candidate scratch instances per candidate-link loop: one `testCandidate` for the current origin/link evaluation and one `bestCandidate` for the cheapest local survivor. When a better local candidate is found, swap scratch references instead of allocating a path object.

Candidate evaluation must be transactional with respect to shared routing state. Today `OsmPath.addAddionalPenalty()` mutates `RoutingContext` fields such as `nogoCost`, `shortestmatch`, `shortestPosition`, `wayfraction`, `isEndpoint`, and `pendingEndpoint` through `distanceBetween()`, `checkPendingEndpoint()`, `setWaypoint()`, and `unsetWaypoint()`. The redesign must either snapshot and restore these fields around each candidate evaluation, or move endpoint/nogo matching into an explicit candidate-local result object. Rejected candidates must not leave changed `RoutingContext` state behind.

### PathModelState

The current `StdPath` and `KinematicPath` subclasses store model-specific state directly on the path object. With accepted/candidate separation, this state needs a small interface that can be copied between accepted records and candidate scratch.

Initial shape:

- `StdPathState`: `ehbd`, `ehbu`, `totalTime`, `totalEnergy`, `elevationBuffer`, `uphillcostdiv`, `downhillcostdiv`
- `KinematicPathState`: `ekin`, `totalTime`, `totalEnergy`, `floatingAngleLeft`, `floatingAngleRight`

The model owns operations for:

- initializing candidate model state from an accepted origin
- resetting candidate state
- snapshotting candidate model state into an accepted record
- comparing dominance with model-specific `definitelyWorseThan`
- applying section and target-node costs

This keeps the redesign open for kinematic routing without forcing all state into `StdPath`.

### PathTraceBuilder

`OsmPathElement` becomes an output/detail artifact, not the primary search history.

`PathTraceBuilder` converts an `AcceptedPath` parent chain into an `OsmPathElement` chain when needed:

- final `compileTrack`
- cost-cutting `matchPath`
- guide-track detours
- detail-mode transfer nodes and voice hints
- track merge operations that still consume `OsmPathElement`

For non-detail search, traceback should walk accepted parent pointers and create `OsmPathElement`s only at conversion time. For detail-mode routing, the builder can retain section-level message/detail payloads from accepted records so voice hints remain correct.

`PathTraceBuilder` needs separate conversion modes:

- final track conversion, which returns the full accepted parent chain with cumulative cost, time, energy, and messages;
- match-path conversion, which returns the prefix chain required by `mergeTrack()` and preserves the edge identity currently derived from `match.idFromPos` and `match.origin?.idFromPos`;
- detour conversion, which converts rejected off-guide candidates into a section chain that `OsmTrack.processVoiceHints()` can inspect with `startSection()`;
- direct-link conversion, which preserves direct-segment `MessageData`, `direct_segment` tags, link distance, and route-local segment numbering.

Detail-mode transfer-node output must be represented explicitly before search-time `OsmPathElement` creation is removed. The detail payload needs ordered section entries containing position, cost, message, time, energy, and whether the section came from a transfer node, shortest-match endpoint, or direct segment.

## Routing Flow

1. Start paths are created as `AcceptedPath` records and attached to the initial link holders/open set.
2. The open set pops an `AcceptedPath`.
3. Invalidated accepted paths are skipped as today via `airdistance == -1`.
4. Current-link link holders are iterated as accepted origins.
5. For each origin/link pair, `PathCandidate.evaluate(origin, link, refTrack, detailMode, rc)` computes the new state in scratch.
6. Failed candidates are discarded without allocation.
7. The best local candidate for a target link is kept in scratch.
8. Radius, cutoff, final-link, and dominance checks run against the scratch candidate and existing accepted dominators.
9. Only surviving candidates snapshot into an `AcceptedPath`.
10. The accepted record is attached to the link holder and `openSet`.
11. Final track, match path, and detours are generated through `PathTraceBuilder`.

Candidate evaluation is isolated from shared mutable context. Before evaluating a candidate that can call endpoint/nogo matching, the routing loop must either create a `RoutingEvaluationState` snapshot or use a candidate-local matching API. After rejection, the shared `RoutingContext` must be equivalent to its pre-evaluation state.

## Data Flow And Ownership

`PathCandidate` has one owner: the routing loop. It can be reused immediately after a candidate is rejected or snapshot.

`AcceptedPath` ownership is shared by the routing search structures. It is immutable enough that heap/link-holder references remain safe. Fields used for invalidation, such as `airdistance`, may stay mutable during the migration, but accepted-path model state and parent links should not be rewritten after insertion.

Link-holder chains are route-search-local state attached to graph links. The redesign must preserve the current cleanup boundary: link holders may remain attached while a search pass is active, while the owning graph cache and linked graph are cleaned between passes by the existing cache cleanup flow. The implementation plan must identify the exact cleanup points for normal routing, guide-track retracking, nearby-track partial recalcs, and cache reuse before replacing the holder type.

`OsmPathElement` ownership belongs to output structures: `OsmTrack.nodes`, detour maps, cost-cutting `matchPath`, and serialized tracks. It should not be required for normal non-detail candidate evaluation.

## Compatibility Strategy

The first implementation phase should not remove `OsmPath` immediately. Instead:

1. Introduce `AcceptedPath` and `PathCandidate` alongside `OsmPath`.
2. Route `StdModel` through the new types while keeping adapters where `RoutingEngine` still expects `OsmPath`-like behavior.
3. Keep `OsmLinkHolder` unchanged initially by letting `AcceptedPath` implement it.
4. Keep `SortedHeap<AcceptedPath>` equivalent behavior.
5. Convert `compileTrack`, `mergeTrack`, guide detours, and cost-cutting through `PathTraceBuilder`.
6. Remove old `originElement`/`myElement` materialization once all consumers use parent-chain conversion.
7. Keep kinematic routing on the existing `OsmPath` path until the accepted/candidate interfaces have explicit support for `KinematicPrePath`, `firstPrePath`, signed turn-angle state, and `KinematicPathState`. This avoids forcing the first implementation branch to solve both models at once.

## Testing

The redesign should be covered in small steps:

- Unit tests for candidate evaluation not escaping after rejection.
- Unit tests for accepted-path parent chain conversion to `OsmPathElement`.
- Regression tests for `StdPathReuseTest`, `OsmPathElementMaterializationTest`, and existing path element behavior.
- Focused route tests for route 9 / gravel / `pass1coefficient=1.5` to compare distance and timing.
- Existing map/node/codec tests used during the allocation work.
- Behavioral equivalence tests for total cost, node sequence, inverse routing, same-segment routing, cost-cutting merge behavior, guide detours, direct-segment output, and voice hints.
- At least one kinematic-route guard before changing `KinematicPath` or `KinematicPrePath` integration.
- JFR allocation snapshots before and after each major phase.

The route result must remain `distance_m=350191` for the focused route-9 comparison.

## Migration Phases

### Phase 1: Trace Adapter

Build `PathTraceBuilder` while still using existing `OsmPath` parent/materialization state. This isolates output conversion and gives tests for final track, match path, and detour conversion.

### Phase 2: AcceptedPath Shell

Introduce `AcceptedPath` as an adapter-backed search record that mirrors the current `OsmPath` fields. Link holders and open set can begin accepting `AcceptedPath` without changing candidate evaluation yet.

This phase must also document and test link-holder cleanup boundaries. `AcceptedPath` must not outlive the route-search structures that own it, and graph cache cleanup must still clear or discard all holder chains before reused graph links can observe stale accepted records.

### Phase 3: Candidate Scratch

Move `RoutingContext.createPath(origin, link, ...)` into candidate evaluation plus snapshot. Rejected candidates no longer allocate accepted records.

This phase must introduce either `RoutingEvaluationState` snapshot/restore or an equivalent candidate-local endpoint/nogo matching API before candidate scratch is reused across evaluations.

### Phase 4: Remove Search-Time OsmPathElement State

Delete `originElement`, `myElement`, and deferred materialization from the search path. Generate elements only through `PathTraceBuilder`.

This phase must first replace detail-mode transfer-node elements with an explicit ordered detail payload and must preserve detour conversion for voice hints.

### Phase 5: Model State Cleanup

Make `StdPathState` and `KinematicPathState` explicit. Remove remaining inheritance constraints where practical.

## Risks

- Cost-cutting currently reads parent element cost through `originElement`. The replacement must compute equivalent parent cost from accepted parent records.
- Detail-mode routing creates transfer-node elements and messages during evaluation. These details must be captured without forcing normal non-detail search to allocate elements.
- Kinematic routing depends on signed turn angle and pre-path state. It must either migrate with explicit state support or remain on the old path path until covered.
- Link-holder invalidation still mutates accepted records via `airdistance = -1`. This can remain temporarily, but long-term cleanup may want a separate validity marker.
- Candidate evaluation currently has shared `RoutingContext` side effects. Reusing candidate scratch without transactional context handling would corrupt endpoint/nogo state across candidate evaluations.
- Match-path conversion has stricter requirements than final-track conversion because `mergeTrack()` depends on prefix chain order and edge identity.
- Direct beeline routing currently increments `OsmPath.seg` and emits `direct_segment` tags. Moving trace generation out of search must preserve this metadata or replace it with explicit route-local numbering.
- Because route timings are noisy, allocation reduction should be judged with JFR plus repeated non-JFR route timing.

## Open Decisions

- Whether accepted records should be fully immutable after insertion or keep mutable `airdistance` for compatibility.
- Whether detail-mode transfer-node payloads belong directly on `AcceptedPath` or in a separate optional `PathDetail` sidecar.
- Whether endpoint/nogo matching should use snapshot/restore of existing `RoutingContext` fields or a new candidate-local evaluation result.
- Whether direct-segment numbering should preserve the current global `OsmPath.seg` behavior or become route-local explicit state.

## Success Criteria

- Focused tests pass after each phase.
- Route 9 / gravel / `pass1coefficient=1.5` still returns `distance_m=350191`.
- Existing focused route comparisons preserve total cost, node sequence, direct-segment tags, guide detours, match-path merging, and voice-hint-relevant messages.
- Kinematic routing remains on the old path implementation until it has dedicated accepted/candidate support and guard tests.
- `StdModel.createPath()` allocation pressure drops materially because rejected candidates no longer allocate persistent path objects.
- No broader accepted-path pooling is needed for correctness or performance.
