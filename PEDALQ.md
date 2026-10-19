# Beerouter on iOS (PedalQ's changes)

This branch is [Beerouter](https://codeberg.org/jgillich/beerouter) at `940b972` with the changes
[PedalQ](https://pedalq.com) uses to route bikes on the iPhone, offline, on BRouter's routing tiles.
Beerouter is licensed under the Mozilla Public License 2.0 (see LICENSE); it descends from
[BRouter](https://github.com/abrensch/brouter) (MIT, see ATTRIBUTION). These changes are under the same terms.

## Changes
- `core/build.gradle.kts`: `iosArm64` and `iosSimulatorArm64` targets, built as one static
  `PedalqRouting.xcframework` (`./gradlew :core:assemblePedalqRoutingReleaseXCFramework`).
- `BExpressionContext`: the expression caches use a plain map that empties when full instead of
  androidx.collection's `LruCache`, which throws "Have object hashCodes changed?" inside
  Kotlin/Native's `HashMap` rehash under routing load (it works on the JVM).
- `app.pedalq.routing.BikeRouter` (new): a blocking entry point that returns a route as BRouter's
  GeoJSON (line with elevation, `track-length`, `filtered ascend`, `messages` per stretch), with a
  test on the iOS simulator (identical to BRouter: Offenbach → Bad Homburg, trekking, 44,625 m).

Use BRouter 1.7.10's `profiles2` and `lookups.dat` (lookup version 11) with today's brouter.de tiles;
the `lookups.dat` in `misc/profiles2` here is version 10.
