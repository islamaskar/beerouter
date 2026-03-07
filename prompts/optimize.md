The benchmark module contains performance tests of the routing engine. I want you to scan the entire
code of the core module thoroughly and find ways to optimize performance in significant ways.

Code readability and simplicity are more important than raw performance. Write clean, functional
Kotlin code only.

For each improvement, create a commit and attach the benchmark result in the commit's description.

Use any tool at your disposal to find optimizations, e.g. profilers. Install missing tools if
necessary.

Scan for places where parallelization could be worthwhile. We target mobile phones, typically with
2-4 performance cores.

Run routingEqualsBrouterMini before committing to ensure you did not break functionality.

Keep going until you run out of code to optimize, do not stop inbetween.
