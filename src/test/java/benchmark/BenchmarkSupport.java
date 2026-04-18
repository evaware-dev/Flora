package benchmark;

public final class BenchmarkSupport {
    private BenchmarkSupport() {
    }

    public static void consume(BenchmarkSink sink) {
        sink.consume(Integer.bitCount(Integer.parseInt("123")));
    }

    public static void consumeFast(BenchmarkSink sink, int value) {
        sink.consume(value);
    }
}
