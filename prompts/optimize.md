The benchmark module contains performance tests of the routing engine. I want you to scan the entire
code of the core module thoroughly and find ways to optimize performance in significant ways. Do not make
micro-optimizations that are not expected to generate returns.

For each improvement, create a commit and attach the benchmark result in the commit's description.

Use any tool at your disposal to find optimizations, e.g. profilers. Install missing tools if
necessary.

Most of the routing is unlikely to be parallelizable. Nonetheless, scan for places where
parallelization could be worthwhile.

Code readability and simplicity are prioritized over performance. Write clean, functional Kotlin code only.

Run routingEqualsBrouterMini before committing to ensure you did not break functionality.

Keep going until you run out of code to optimize, do not stop inbetween.