This code base was converted from a Java project, most of it is not idiomatic Kotlin. Please scan
the project files and refactor them to follow Kotlin best practises.

Purely functional code is preferred over imperative programming.

Prefer non-nullable types by default; use nullability only when it serves a specific functional
purpose. Prefer val over var.

Prefer data classes when possible.

Java dependencies should be removed since we aim to support Kotlin Native. One exception, do not
remove the brouter dependency for testing. androidx.collection is a multiplatform library, do not
remove it.

File system access should be removed in favor of input streams or the MapAccessFileSystem interface.

Use enum classes instead of consts where appropriate.

Prefer already included libraries over custom implementations. Prefer the spatial-k library
for geospatial calculations.

Some places are using int latitude and longitude. Convert them to use spatial-k `Position`.
You may use the OSM integer id instead of Position.

You should create a Git commit for each major changeset. Before making a commit for a change, look
for other files that need similar refactorings and include them in the commit. It is crucial that
the routing behavior does not change, so you should run the core:routingParityMiniTest Gradle task
before comitting. Every couple of commits, run all other tests as well.

Please do not stop until the code base is fully refactored.
