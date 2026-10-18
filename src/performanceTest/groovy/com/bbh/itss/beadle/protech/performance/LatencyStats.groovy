package com.bbh.itss.beadle.protech.performance

import java.util.concurrent.Callable

import static java.lang.System.nanoTime
import static java.util.concurrent.Executors.newFixedThreadPool
import static java.util.concurrent.TimeUnit.SECONDS

/** The latencies of a number of calls made from a few threads at once, after one warm-up call per thread. */
class LatencyStats {

    final List<Double> millis
    final int errors

    private LatencyStats(List<Double> millis, int errors) {
        this.millis = millis.sort()
        this.errors = errors
    }

    /** Makes {@code calls} calls from {@code threads} threads; a call answers whether it succeeded. */
    static LatencyStats measure(int calls, int threads, Closure<Boolean> call) {
        def pool = newFixedThreadPool(threads)
        try {
            pool.invokeAll((0..<threads).collect { { -> call() } as Callable })*.get()
            def results = pool.invokeAll((0..<calls).collect {
                { ->
                    long begin = nanoTime()
                    boolean ok = call()
                    [(nanoTime() - begin) / 1_000_000d, ok]
                } as Callable<List>
            })*.get()
            new LatencyStats(results.collect { it[0] as double }, results.count { !it[1] } as int)
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(10, SECONDS)
        }
    }

    double getP95() {
        millis[Math.ceil(0.95 * millis.size()) as int - 1]
    }
}
