package benchmark;

public interface IBenchmark {
    void prepare();

    void benchmark(BenchmarkSink sink);

    default void close() {
    }
}
