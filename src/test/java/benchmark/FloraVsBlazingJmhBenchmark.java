package benchmark;

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
public class FloraVsBlazingJmhBenchmark {
    @Param({"0", "1", "8", "64", "200"})
    public int listeners;

    private FloraBus<TestEvent> flora;
    private BlazingBus<TestEvent> blazing;
    private TestEvent event;

    @Setup
    public void setup() {
        event = new TestEvent();

        flora = new FloraBus<>();
        for (int i = 0; i < listeners; i++) {
            flora.subscribe(new Listener<>(0, item -> item.value++, DispatchMode.SYNC));
        }

        blazing = new BlazingBus<>(listeners);
        for (int i = 0; i < listeners; i++) {
            blazing.subscribe(item -> item.value++);
        }
        blazing.seal();
    }

    @Benchmark
    public int blazingPost() {
        event.value = 0;
        blazing.post(event);
        return event.value;
    }

    @Benchmark
    public int floraPost() {
        event.value = 0;
        flora.post(event);
        return event.value;
    }

    public static final class TestEvent {
        public int value;
    }
}
