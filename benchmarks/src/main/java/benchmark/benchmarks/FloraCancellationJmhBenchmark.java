package benchmark.benchmarks;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import sweetie.evaware.flora.FloraConfigurator;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.core.FloraBus;
import sweetie.evaware.flora.core.Listener;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class FloraCancellationJmhBenchmark {
    @Param({"8", "64", "200"})
    public int listeners;

    private FloraBus<CancellableEvent> floraBus;
    private CancellableEvent event;

    @Setup
    public void setup() {
        FloraConfigurator.registerCancellation(CancellableEvent.class, CancellableEvent::isCancelled);

        event = new CancellableEvent();

        floraBus = new FloraBus<>();
        floraBus.subscribe(new Listener<>(100, item -> {
            item.value++;
            item.setCancelled(true);
        }, DispatchMode.SYNC));
        for (int i = 0; i < listeners - 1; i++) {
            floraBus.subscribe(new Listener<>(0, item -> item.value++, DispatchMode.SYNC));
        }
    }

    @Benchmark
    public int cancelledPost() {
        event.value = 0;
        event.setCancelled(false);
        floraBus.post(event);
        return event.value;
    }

    public static final class CancellableEvent {
        public int value;
        private boolean cancelled;

        public boolean isCancelled() {
            return cancelled;
        }

        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }
    }
}
