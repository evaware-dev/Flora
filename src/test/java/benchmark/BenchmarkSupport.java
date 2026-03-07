package benchmark;

import org.openjdk.jmh.infra.Blackhole;

public final class BenchmarkSupport {
    private BenchmarkSupport() {
    }

    public static void consume(Blackhole blackhole) {
        blackhole.consume(Integer.bitCount(Integer.parseInt("123")));
    }
}
