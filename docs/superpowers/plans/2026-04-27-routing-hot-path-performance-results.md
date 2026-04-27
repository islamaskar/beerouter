# Routing Hot Path Performance Results

## Pre-Optimization Reference

Source: earlier Beerouter route 9 / gravel / pass1coefficient 1.5 run before hot-path allocation changes.

| elapsed_ms | links_processed | pass1_ms | pass1_expansions | pass2_ms | pass2_expansions | re_tracking_ms | re_tracking_expansions |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 41070 | 18667888 | 15715 | 7040706 | 25319 | 11624778 | 36 | 1905 |

## Post-Optimization Runs

Command: `./gradlew :benchmark:singleRoutePerformance -PprofileArgs="/brouter/tmp-segments /brouter/misc/profiles2 gravel"`

| Run | elapsed_ms | links_processed | pass1_ms | pass1_expansions | pass2_ms | pass2_expansions | re_tracking_ms | re_tracking_expansions |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 32405 | 18667888 | 12900 | 7040706 | 19291 | 11624778 | 64 | 1905 |
| 2 | 32089 | 18667888 | 12220 | 7040706 | 19673 | 11624778 | 58 | 1905 |
| 3 | 32375 | 18667888 | 12768 | 7040706 | 19405 | 11624778 | 65 | 1905 |

## JFR Allocation Snapshot

Command: `./gradlew :benchmark:singleRoutePerformance -PprofileArgs="/brouter/tmp-segments /brouter/misc/profiles2 gravel" -PjfrFile=/work/beerouter-route9-after.jfr`

| Class | Before allocation pressure | After allocation pressure |
|---|---:|---:|
| `dev.skynomads.beerouter.geo.Position` | 24.24% | 16.46% |
| `java.lang.Short` | 10.29% | 0.00% |
| `dev.skynomads.beerouter.map.OsmNodesMap$DfsFrame` | 5.07% | 2.30% |
| `dev.skynomads.beerouter.expressions.BExpressionContext$ByteArrayKey` | 1.70% | 0.00% |

Remaining top allocation pressure after optimization: `StdPath` 32.15%, `Object[]` 16.75%, `Position` 16.46%, `OsmNode` 12.32%, `OsmPathElement` 8.99%.
