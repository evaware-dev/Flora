package benchmark.api;

public final class Blackhole {
    private long value;

    public void consume(int input) {
        value = value * 31L + input;
    }

    public long value() {
        return value;
    }
}
