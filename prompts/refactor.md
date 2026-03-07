This code base was converted from a Java project, most of it is not idiomatic Kotlin. Please scan
the project files and refactor them to follow Kotlin best practises.

Replace any code marked as deprecated.

Purely functional code is preferred over imperative programming.

Prefer non-nullable types by default; use nullability only when it serves a specific functional
purpose. Prefer val over var.

Prefer data classes when possible.

Java dependencies should be removed since we aim to support Kotlin Native. The currently targeted
platforms are JVM and Linux Native. Do not remove the brouter dependency for testing.
androidx.collection is a multiplatform library, do not remove it. Do not replace java.net.URI with
io.ktor.client.engine.cio.CIO, it does not support TLS.

File system access should be removed in favor of input streams or the MapAccessFileSystem interface.

Use enum classes instead of consts where appropriate.

Coordinates were originally Integer (E6 offset). We have begun the migration to using spatial-k
Position, which stores coordinates as Double, but it appears that this is suboptimal in a routing
engine. At `dev.skynomads.beerouter.geo.Position` is a new Position type, please make sure it is
used across the code base. Conversions between different types should be avoided. Keeping
coordinates stored as individual Integer fields should also be avoided. If a function takes in
coordinates and the value is available as a Position in the caller, it should take in geo.Position
instead.

You should create a Git commit for each major changeset. Before making a commit for a change, look
for other files that need similar refactorings and include them in the commit. It is crucial that
the routing behavior does not change, so you should run the core:routingParityMiniTest Gradle task
before comitting. Every couple of commits, run all other tests as well.

Often, the code creates unnecessary variables. Please remove them. For example:

```kotlin
val nodeLon = node.position.longitude
val nodeLat = node.position.latitude
```

Please do not stop until the code base is fully refactored.
