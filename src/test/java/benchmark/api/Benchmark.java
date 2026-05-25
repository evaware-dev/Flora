package benchmark.api;

import benchmark.Constants;

public abstract class Benchmark<T extends BenchmarkEvent> implements IBenchmark {
    protected final T event;

    protected Benchmark(T event) {
        this.event = event;
    }

    @Override
    public final void prepare() {
        setup(Constants.LISTENERS);
    }

    @Override
    public final void benchmark(Blackhole sink) {
        event.sink = sink;
        post(event);
    }

    protected abstract void setup(int listeners);

    protected abstract void post(T event);

    protected final void consume(T event) {
        BenchmarkSupport.consumeFast(event.sink, event.payload);
    }
}
