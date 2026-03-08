package dev.skynomads.beerouter.benchmark

import dev.skynomads.beerouter.geo.CheapRuler
import dev.skynomads.beerouter.geo.Position
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import kotlin.random.Random

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
open class CheapRulerBenchmark {

    // ── Inputs ────────────────────────────────────────────────────────────────

    // Single pair — isolates pure arithmetic overhead with no iteration noise
    private lateinit var p1: Position
    private lateinit var p2: Position

    // Batch arrays — reveals memory-access / cache behaviour at scale
    private lateinit var positions: Array<Position>

    // Destination inputs
    private var distance: Double = 0.0
    private var angle: Double = 0.0

    @Setup(Level.Trial)
    fun setup() {
        val rng = Random(42)

        // Random positions anywhere on Earth in integer lat/lon
        // iLat: 0..180_000_000  (−90° … +90°)
        // iLon: 0..360_000_000  (  0° … +360°, or −180°…+180° depending on convention)
        fun randomPosition() = Position(
            longitude = rng.nextInt(360_000_000),
            latitude = rng.nextInt(180_000_000),
        )

        p1 = randomPosition()
        p2 = randomPosition()

        // 10 000 positions for batch benchmarks
        positions = Array(10_000) { randomPosition() }

        distance = rng.nextDouble(1.0, 50_000.0)   // 1 m … 50 km
        angle = rng.nextDouble(0.0, 360.0)
    }

    // ── distance ─────────────────────────────────────────────────────────────

    /** Baseline: single distance call. */
    @Benchmark
    fun distanceSingle(bh: Blackhole) {
        bh.consume(CheapRuler.distance(p1, p2))
    }

    /**
     * Consecutive pairs across an array — the real hot-path in routing.
     * Exercises cache-line re-use of kxCache/kyCache across nearby latitudes.
     */
    @Benchmark
    fun distanceBatch(bh: Blackhole) {
        var i = 0
        while (i < positions.size - 1) {
            bh.consume(CheapRuler.distance(positions[i], positions[i + 1]))
            i++
        }
    }

    /**
     * Same latitude bucket for every pair — best-case branch / cache scenario.
     * Useful to see the floor: how fast is the sqrt + two multiplies alone?
     */
    @Benchmark
    fun distanceSameLatBucket(bh: Blackhole) {
        // Force both points into the equator bucket
        val equator = Position(p1.longitude, 90_000_000)   // 0° latitude
        val nearby = Position(p2.longitude, 90_050_000)
        repeat(1_000) {
            bh.consume(CheapRuler.distance(equator, nearby))
        }
    }

    // ── cacheIndex / getLonLatToMeterScales ───────────────────────────────────

    /**
     * Isolates the index lookup itself (integer divide + coerceIn).
     * Compare old vs new by swapping in getLonLatToMeterScales manually.
     */
    @Benchmark
    fun cacheIndexOnly(bh: Blackhole) {
        // Accessing internal helpers via the public surface that exercises the
        // same arithmetic — distance with identical points gives 0 but still
        // hits the index path.
        bh.consume(CheapRuler.distance(p1, p1))
    }

    // ── destination ──────────────────────────────────────────────────────────

    @Benchmark
    fun destinationSingle(bh: Blackhole) {
        bh.consume(CheapRuler.destination(p1, distance, angle))
    }

    @Benchmark
    fun destinationBatch(bh: Blackhole) {
        for (pos in positions) {
            bh.consume(CheapRuler.destination(pos, distance, angle))
        }
    }

    // ── Old vs New: DoubleArray cache vs flat arrays ──────────────────────────
    //
    // To compare the two implementations side-by-side, duplicate CheapRuler
    // as CheapRulerOld (with Array<DoubleArray> + getLonLatToMeterScales) and
    // add mirror benchmarks below:
    //
    // @Benchmark
    // fun distanceSingle_old(bh: Blackhole) =
    //     bh.consume(CheapRulerOld.distance(p1, p2))
    //
    // @Benchmark
    // fun distanceBatch_old(bh: Blackhole) {
    //     var i = 0
    //     while (i < positions.size - 1) {
    //         bh.consume(CheapRulerOld.distance(positions[i], positions[i + 1]))
    //         i++
    //     }
    // }
}
