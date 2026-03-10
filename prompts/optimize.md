The benchmark module contains performance tests of the routing engine. I want you to scan the entire
code of the core module thoroughly and find ways to optimize performance in significant ways.

Write clean, functional Kotlin code. Do not sacrifice readability for performance.

Every time you achieve at least 1% improvement over the base benchmark, create a commit that
describes the changes. Before you commit, run the benchmark a second time to verify.

Use any tool at your disposal to find optimizations, e.g. profilers. Install missing tools if
necessary. You are running in an Ubuntu container with full root access.

Attempt to implement parallelization in the routing engine. Note that there is shared
state, so create a thorough plan before you attempt this. We target mobile phones, typically with
2-4 performance cores.

Run routingEqualsBrouterMini before committing to ensure you did not break functionality.

Keep going until you run out of code to optimize, do not stop.
