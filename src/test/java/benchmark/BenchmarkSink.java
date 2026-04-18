package benchmark;

public final class BenchmarkSink {
    private long value;

    public void consume(int input) {
        value = value * 31L + input;
    }

    public long value() {
        return value;
    }
}
