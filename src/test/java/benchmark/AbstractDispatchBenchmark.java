package benchmark;

public abstract class AbstractDispatchBenchmark<T extends BenchmarkEvent> implements IBenchmark {
    protected final T event;

    protected AbstractDispatchBenchmark(T event) {
        this.event = event;
    }

    @Override
    public final void prepare() {
        setup(Constants.LISTENERS);
    }

    @Override
    public final void benchmark(BenchmarkSink sink) {
        event.sink = sink;
        post(event);
    }

    protected abstract void setup(int listeners);

    protected abstract void post(T event);

    protected final void consume(T event) {
        BenchmarkSupport.consumeFast(event.sink, event.payload);
    }
}
