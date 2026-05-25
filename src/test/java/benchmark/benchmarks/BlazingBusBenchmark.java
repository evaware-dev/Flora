package benchmark.benchmarks;

import benchmark.api.*;
import benchmark.blazebus.BlazingBus;

public final class BlazingBusBenchmark extends Benchmark<BenchmarkEvent> {
    private BlazingBus<BenchmarkEvent> bus;

    public BlazingBusBenchmark() {
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
