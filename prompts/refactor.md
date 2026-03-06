This code base was converted from a Java project, most of it is not idiomatic Kotlin. Please go
through every file and refactor it to follow Kotlin best practises.

Purely functional code is preferred over imperative programming.

Prefer non-nullable types by default; use nullability only when it serves a specific functional
purpose. Prefer val over var.

Prefer data classes when possible. Many classes use mutable fields that can be converted to data
classes.

Java dependencies should be removed since we aim to support Kotlin Native. One exception, do not
remove the brouter dependency for testing.

Direct file access should be removed in favor of input streams. Only callers of the library like
tests and benchmarks are allowed direct file access.

Use proper enum classes instead of consts where appropriate.

You should create a Git commit for each major changeset. Before making a commit for a change, look
for other files that need similar refactorings and include them in the commit. It is crucial that
the routing behavior does not change, so you should run RoutingTest before comitting to verify the
code still works as intended. Every couple of commits, run all other tests as well.

Please do not stop until the code base is fully refactored. I repeat, do not stop early.
