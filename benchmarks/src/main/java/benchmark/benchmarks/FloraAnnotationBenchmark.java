package benchmark.benchmarks;

import benchmark.api.Benchmark;
import benchmark.api.BenchmarkEvent;
import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.api.Commando;

public final class FloraAnnotationBenchmark extends Benchmark<FloraAnnotationBenchmark.FloraAnnotationEvent> {
    public FloraAnnotationBenchmark() {
        super(new FloraAnnotationEvent());
    }

    @Override
    protected void setup(int listeners) {
        for (int i = 0; i < listeners; i++) {
            Flora.register(new ListenerTarget());
        }
    }

    @Override
    protected void post(FloraAnnotationEvent event) {
        Flora.post(event);
    }

    public final class ListenerTarget {
        @Commando
        public void onEvent(FloraAnnotationEvent event) {
            consume(event);
        }
    }

    public static final class FloraAnnotationEvent extends BenchmarkEvent {
    }
}
