package benchmark.api;

public interface IBenchmark {
    void prepare();

    void benchmark(Blackhole sink);

    default void close() {
    }
}
