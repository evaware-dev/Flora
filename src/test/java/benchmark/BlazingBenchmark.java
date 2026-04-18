package benchmark;

public final class BlazingBenchmark extends AbstractDispatchBenchmark<BenchmarkEvent> {
    private BlazingBus<BenchmarkEvent> bus;

    public BlazingBenchmark() {
        super(new BenchmarkEvent());
    }

    @Override
    protected void setup(int listeners) {
        bus = new BlazingBus<>(listeners);
        for (int i = 0; i < listeners; i++) {
            bus.subscribe(this::consume);
        }
        bus.seal();
    }

    @Override
    protected void post(BenchmarkEvent event) {
        bus.post(event);
    }
}
