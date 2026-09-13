package benchmark.benchmarks;

import benchmark.api.*;

import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;

public final class FloraBusBenchmark extends Benchmark<FloraBusBenchmark.FloraEvent> {
    public FloraBusBenchmark() {
        super(new FloraEvent());
    }

    @Override
    protected void setup(int listeners) {
        FloraBus<FloraEvent> bus = Flora.getBus(FloraEvent.class);
        for (int i = 0; i < listeners; i++) {
            bus.subscribe(new Listener<>(0, this::consume, DispatchMode.SYNC));
        }
    }

    @Override
    protected void post(FloraEvent event) {
        Flora.post(event);
    }

    public static final class FloraEvent extends BenchmarkEvent {
    }
}
