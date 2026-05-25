package benchmark.api;

public final class BenchmarkSupport {
    private BenchmarkSupport() {
    }

    public static void consume(Blackhole sink) {
        sink.consume(Integer.bitCount(Integer.parseInt("123")));
    }

    public static void consumeFast(Blackhole sink, int value) {
        sink.consume(value);
    }
}
