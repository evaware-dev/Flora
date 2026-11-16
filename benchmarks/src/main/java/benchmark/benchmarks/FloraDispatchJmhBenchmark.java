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
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import sweetie.evaware.flora.Flora;
import sweetie.evaware.flora.api.DispatchMode;
import sweetie.evaware.flora.api.Subscription;
import sweetie.evaware.flora.core.EventBus;
import sweetie.evaware.flora.core.Listener;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(1)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
public class FloraDispatchJmhBenchmark {
    @Param({"0", "1", "8", "64", "200"})
    public int listeners;

    private EventBus<TestEvent> directBus;
    private Subscription[] globalSubscriptions;
    private TestEvent event;

    @Setup
    public void setup() {
        event = new TestEvent();

        directBus = new EventBus<>();
        for (int i = 0; i < listeners; i++) {
            directBus.subscribe(new Listener<>(0, item -> item.value++, DispatchMode.SYNC));
        }

        globalSubscriptions = new Subscription[listeners];
        for (int i = 0; i < listeners; i++) {
            globalSubscriptions[i] = Flora.bus(TestEvent.class)
                    .subscribe(new Listener<>(0, item -> item.value++, DispatchMode.SYNC));
        }
    }

    @TearDown
    public void tearDown() {
        for (Subscription subscription : globalSubscriptions) {
            subscription.unsubscribe();
        }
    }

    @Benchmark
    public int directPost() {
        event.value = 0;
        directBus.post(event);
        return event.value;
    }

    @Benchmark
    public int globalPost() {
        event.value = 0;
        Flora.post(event);
        return event.value;
    }

    public static final class TestEvent {
        public int value;
    }
}
